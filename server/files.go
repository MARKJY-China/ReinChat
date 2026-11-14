package main

import (
	"bytes"
	"database/sql"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
)

func (s *Server) handleUploadFile(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if r.ContentLength > maxUploadBodyBytes {
		writeAPIError(w, http.StatusRequestEntityTooLarge, "request_too_large", "upload is too large")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxUploadBodyBytes)

	reader, err := r.MultipartReader()
	if err != nil {
		writeAPIError(w, http.StatusBadRequest, "invalid_request", "expected multipart/form-data with a file field")
		return
	}

	var tempPath string
	var size int64
	var mimeType string
	defer func() {
		if tempPath != "" {
			_ = os.Remove(tempPath)
		}
	}()

	found := false
	for {
		part, nextErr := reader.NextPart()
		if nextErr == io.EOF {
			break
		}
		if nextErr != nil {
			if isMaxBytesError(nextErr) {
				writeAPIError(w, http.StatusRequestEntityTooLarge, "request_too_large", "upload is too large")
			} else {
				writeAPIError(w, http.StatusBadRequest, "invalid_request", "invalid multipart upload")
			}
			return
		}
		if part.FormName() != "file" {
			_ = part.Close()
			continue
		}
		if found {
			_ = part.Close()
			writeAPIError(w, http.StatusBadRequest, "invalid_request", "only one file field is allowed")
			return
		}
		found = true

		tmp, createErr := os.CreateTemp(s.filesDir, ".upload-*")
		if createErr != nil {
			_ = part.Close()
			s.internalError(w, r, createErr)
			return
		}
		tempPath = tmp.Name()
		_ = tmp.Chmod(0o600)
		n, copyErr := io.Copy(tmp, io.LimitReader(part, maxUploadBytes+1))
		_ = part.Close()
		if copyErr != nil {
			_ = tmp.Close()
			if isMaxBytesError(copyErr) {
				writeAPIError(w, http.StatusRequestEntityTooLarge, "request_too_large", "upload is too large")
			} else {
				writeAPIError(w, http.StatusBadRequest, "invalid_request", "could not read uploaded file")
			}
			return
		}
		if n > maxUploadBytes {
			_ = tmp.Close()
			writeAPIError(w, http.StatusRequestEntityTooLarge, "file_too_large", "file must be at most 10 MiB")
			return
		}
		if n == 0 {
			_ = tmp.Close()
			writeAPIError(w, http.StatusBadRequest, "invalid_image", "uploaded file is empty")
			return
		}
		if err := tmp.Sync(); err != nil {
			_ = tmp.Close()
			s.internalError(w, r, err)
			return
		}
		if _, err := tmp.Seek(0, io.SeekStart); err != nil {
			_ = tmp.Close()
			s.internalError(w, r, err)
			return
		}
		header := make([]byte, 512)
		read, readErr := tmp.Read(header)
		if readErr != nil && readErr != io.EOF {
			_ = tmp.Close()
			s.internalError(w, r, readErr)
			return
		}
		if err := tmp.Close(); err != nil {
			s.internalError(w, r, err)
			return
		}
		mimeType = detectImageMIME(header[:read])
		if mimeType == "" {
			writeAPIError(w, http.StatusUnsupportedMediaType, "unsupported_media_type", "only JPEG, PNG, and WebP images are accepted")
			return
		}
		size = n
	}

	if !found {
		writeAPIError(w, http.StatusBadRequest, "file_required", "multipart field 'file' is required")
		return
	}

	fileID, err := newUUID()
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	finalPath := filepath.Join(s.filesDir, fileID)
	if err := os.Rename(tempPath, finalPath); err != nil {
		s.internalError(w, r, err)
		return
	}
	tempPath = "" // renamed; the deferred cleanup must not remove it on DB failure below
	if _, err := s.db.ExecContext(r.Context(),
		`INSERT INTO files (id, user_id, mime_type, size_bytes, file_name, created_at)
		 VALUES (?, ?, ?, ?, ?, ?)`,
		fileID, user.ID, mimeType, size, fileID, s.now().UnixMilli()); err != nil {
		_ = os.Remove(finalPath)
		s.internalError(w, r, err)
		return
	}

	writeJSON(w, http.StatusCreated, FileUploadResponse{ID: fileID, MimeType: mimeType, Size: size})
}

func (s *Server) handleDownloadFile(w http.ResponseWriter, r *http.Request, fileID string) {
	if r.Method != http.MethodGet {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if !isUUID(fileID) {
		writeAPIError(w, http.StatusNotFound, "file_not_found", "file not found")
		return
	}
	var mimeType, fileName string
	err := s.db.QueryRowContext(r.Context(),
		`SELECT mime_type, file_name FROM files WHERE id = ? AND user_id = ?`,
		fileID, user.ID).Scan(&mimeType, &fileName)
	if errors.Is(err, sql.ErrNoRows) {
		writeAPIError(w, http.StatusNotFound, "file_not_found", "file not found")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	if fileName != fileID || !isUUID(fileName) || filepath.Base(fileName) != fileName {
		s.internalError(w, r, errors.New("invalid stored file name"))
		return
	}
	fullPath := filepath.Join(s.filesDir, fileName)
	f, err := os.Open(fullPath)
	if errors.Is(err, os.ErrNotExist) {
		writeAPIError(w, http.StatusNotFound, "file_not_found", "file not found")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil || !info.Mode().IsRegular() {
		s.internalError(w, r, errors.New("stored file is not a regular file"))
		return
	}

	w.Header().Set("Content-Type", mimeType)
	w.Header().Set("Content-Disposition", "inline; filename=\""+fileID+"\"")
	w.Header().Set("Cache-Control", "private, max-age=31536000, immutable")
	http.ServeContent(w, r, fileID, info.ModTime(), f)
}

func detectImageMIME(data []byte) string {
	switch {
	case len(data) >= 3 && data[0] == 0xff && data[1] == 0xd8 && data[2] == 0xff:
		return "image/jpeg"
	case len(data) >= 8 && bytes.Equal(data[:8], []byte{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}):
		return "image/png"
	case len(data) >= 12 && bytes.Equal(data[:4], []byte("RIFF")) && bytes.Equal(data[8:12], []byte("WEBP")):
		return "image/webp"
	default:
		return ""
	}
}

func isMaxBytesError(err error) bool {
	var maxErr *http.MaxBytesError
	return errors.As(err, &maxErr)
}
