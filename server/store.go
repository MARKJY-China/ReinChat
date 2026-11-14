package main

import (
	"context"
	"database/sql"
	"embed"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

//go:embed schema.sql
var schemaFS embed.FS

func openStore(dataDir string) (*sql.DB, error) {
	if strings.TrimSpace(dataDir) == "" {
		return nil, errors.New("data directory must not be empty")
	}
	absDir, err := filepath.Abs(dataDir)
	if err != nil {
		return nil, fmt.Errorf("resolve data directory: %w", err)
	}
	// The SQLite DSN is a URI. These characters would silently change its
	// meaning, so reject an unusual data path instead of writing elsewhere.
	if strings.ContainsAny(absDir, "?#%") {
		return nil, errors.New("data directory path must not contain '#', '?', or '%'")
	}
	if err := os.MkdirAll(absDir, 0o700); err != nil {
		return nil, fmt.Errorf("create data directory: %w", err)
	}
	// Best effort tightening for an existing installation.
	_ = os.Chmod(absDir, 0o700)

	dbPath := filepath.Join(absDir, "reinchat.db")
	dsn := sqliteDSN(dbPath)

	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	db.SetMaxOpenConns(8)
	db.SetMaxIdleConns(8)
	db.SetConnMaxLifetime(0)

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("connect to sqlite: %w", err)
	}
	if err := migrate(ctx, db); err != nil {
		_ = db.Close()
		return nil, err
	}
	if _, err := db.ExecContext(ctx,
		`ALTER TABLE users ADD COLUMN level INTEGER NOT NULL DEFAULT 1`); err != nil &&
		!strings.Contains(strings.ToLower(err.Error()), "duplicate column") {
		_ = db.Close()
		return nil, fmt.Errorf("ensure users.level: %w", err)
	}
	_ = os.Chmod(dbPath, 0o600)
	return db, nil
}

func sqliteDSN(path string) string {
	// Use a plain SQLite file URI. net/url's URL.String would turn a Windows
	// path such as C:/data into file://C:/data, where C: is parsed as a host.
	// The _pragma parameters apply to every pooled connection, not only the
	// first.
	return "file:" + filepath.ToSlash(path) +
		"?_pragma=busy_timeout(5000)" +
		"&_pragma=journal_mode(WAL)" +
		"&_pragma=synchronous(NORMAL)" +
		"&_pragma=foreign_keys(1)"
}

func migrate(ctx context.Context, db *sql.DB) error {
	raw, err := schemaFS.ReadFile("schema.sql")
	if err != nil {
		return fmt.Errorf("read embedded schema: %w", err)
	}
	for _, stmt := range strings.Split(string(raw), ";") {
		stmt = strings.TrimSpace(stmt)
		if stmt == "" || strings.HasPrefix(stmt, "--") && !strings.Contains(stmt, "\n") {
			continue
		}
		if _, err := db.ExecContext(ctx, stmt); err != nil {
			return fmt.Errorf("apply database migration: %w", err)
		}
	}
	return nil
}
