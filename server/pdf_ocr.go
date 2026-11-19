package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

type pdfOCRResponse struct {
	Text  string `json:"text"`
	Pages int    `json:"pages"`
}

func (s *Server) handlePDFOCR(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w, http.MethodPost)
		return
	}
	if _, ok := s.requireAuth(w, r); !ok {
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, 45<<20)
	file, _, err := r.FormFile("file")
	if err != nil {
		writeAPIError(w, http.StatusBadRequest, "file_required", "PDF file is required")
		return
	}
	defer file.Close()
	tempDir, err := os.MkdirTemp("", "reinchat-pdf-*")
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer os.RemoveAll(tempDir)
	pdfPath := filepath.Join(tempDir, "input.pdf")
	out, err := os.Create(pdfPath)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	if _, err := io.Copy(out, io.LimitReader(file, 40<<20)); err != nil {
		out.Close()
		s.internalError(w, r, err)
		return
	}
	out.Close()

	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Minute)
	defer cancel()
	prefix := filepath.Join(tempDir, "page")
	cmd := exec.CommandContext(ctx, "pdftoppm", "-jpeg", "-r", "110", pdfPath, prefix)
	if output, err := cmd.CombinedOutput(); err != nil {
		writeAPIError(w, http.StatusBadGateway, "pdf_render_failed", strings.TrimSpace(string(output)))
		return
	}
	images, _ := filepath.Glob(prefix + "-*.jpg")
	sort.Strings(images)
	if len(images) > 200 {
		images = images[:200]
	}
	results := make([]string, len(images))
	var wg sync.WaitGroup
	sem := make(chan struct{}, 4)
	for index, image := range images {
		wg.Add(1)
		go func(index int, image string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			ocr := exec.CommandContext(ctx, "tesseract", image, "stdout", "-l", "chi_sim+eng", "--psm", "6")
			raw, err := ocr.Output()
			if err == nil {
				results[index] = strings.TrimSpace(string(raw))
			}
		}(index, image)
	}
	wg.Wait()
	var text strings.Builder
	for index, body := range results {
		if body != "" {
			fmt.Fprintf(&text, "\n--- 第 %d 页 ---\n%s", index+1, body)
		}
		if text.Len() > 80000 {
			break
		}
	}
	result := strings.TrimSpace(text.String())
	if result == "" {
		writeAPIError(w, http.StatusUnprocessableEntity, "ocr_empty", "没有识别出文字")
		return
	}
	if len([]rune(result)) > 60000 {
		result = string([]rune(result)[:60000])
	}
	writeJSON(w, http.StatusOK, pdfOCRResponse{Text: result, Pages: len(images)})
}
