package main

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"
)

const (
	providerKeyVersion = byte(1)
	tokenRandomBytes   = 32
)

type dataCipher struct {
	aead cipher.AEAD
}

func newDataCipher(masterKey []byte) (*dataCipher, error) {
	if len(masterKey) != 32 {
		return nil, errors.New("master key must be exactly 32 bytes")
	}
	block, err := aes.NewCipher(masterKey)
	if err != nil {
		return nil, fmt.Errorf("create AES cipher: %w", err)
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("create AES-GCM: %w", err)
	}
	return &dataCipher{aead: aead}, nil
}

func (c *dataCipher) seal(plaintext, aad []byte) ([]byte, error) {
	nonce := make([]byte, c.aead.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("generate encryption nonce: %w", err)
	}
	// Version byte allows a future key/cipher migration without ambiguity.
	out := make([]byte, 0, 1+len(nonce)+len(plaintext)+c.aead.Overhead())
	out = append(out, providerKeyVersion)
	out = append(out, nonce...)
	out = c.aead.Seal(out, nonce, plaintext, aad)
	return out, nil
}

func (c *dataCipher) open(ciphertext, aad []byte) ([]byte, error) {
	nonceSize := c.aead.NonceSize()
	if len(ciphertext) < 1+nonceSize+c.aead.Overhead() || ciphertext[0] != providerKeyVersion {
		return nil, errors.New("invalid encrypted value")
	}
	nonce := ciphertext[1 : 1+nonceSize]
	payload := ciphertext[1+nonceSize:]
	plaintext, err := c.aead.Open(nil, nonce, payload, aad)
	if err != nil {
		return nil, errors.New("decrypt encrypted value: authentication failed")
	}
	return plaintext, nil
}

func providerAAD(userID int64, providerID string) []byte {
	return []byte(fmt.Sprintf("reinchat-provider:%d:%s", userID, providerID))
}

func generateToken() (raw string, hash [32]byte, err error) {
	buf := make([]byte, tokenRandomBytes)
	if _, err = rand.Read(buf); err != nil {
		return "", hash, fmt.Errorf("generate token: %w", err)
	}
	raw = base64.RawURLEncoding.EncodeToString(buf)
	hash = sha256.Sum256([]byte(raw))
	return raw, hash, nil
}

func hashToken(raw string) [32]byte {
	return sha256.Sum256([]byte(raw))
}

func newUUID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("generate UUID: %w", err)
	}
	b[6] = (b[6] & 0x0f) | 0x40 // version 4
	b[8] = (b[8] & 0x3f) | 0x80 // RFC 4122 variant
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}

func isUUID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for i := 0; i < len(s); i++ {
		switch i {
		case 8, 13, 18, 23:
			if s[i] != '-' {
				return false
			}
		default:
			c := s[i]
			if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
				return false
			}
		}
	}
	return true
}

func truncateRunes(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n])
}

func normalizePreviewText(s string) string {
	return strings.Join(strings.Fields(s), " ")
}
