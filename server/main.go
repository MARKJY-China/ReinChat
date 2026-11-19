package main

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"
)

const maxTokenTTLHours = 24 * 365 * 100

type envConfig struct {
	Addr             string
	DataDir          string
	MasterKey        []byte
	TokenHours       int
	AdminToken       string
	DSHGatewayURL    string
	DeepSeekToolsKey string
}

func main() {
	logger := log.New(os.Stdout, "", log.LstdFlags|log.LUTC)
	if err := run(logger); err != nil {
		logger.Printf("fatal: %v", err)
		os.Exit(1)
	}
}

func run(logger *log.Logger) error {
	env, err := loadEnvConfig()
	if err != nil {
		return err
	}
	app, err := NewServer(Config{
		DataDir:          env.DataDir,
		MasterKey:        env.MasterKey,
		TokenTTL:         time.Duration(env.TokenHours) * time.Hour,
		BcryptCost:       productionBcryptCost,
		AdminToken:       env.AdminToken,
		DSHGatewayURL:    env.DSHGatewayURL,
		DeepSeekToolsKey: env.DeepSeekToolsKey,
		Logger:           logger,
	})
	if err != nil {
		return err
	}
	defer app.Close()

	httpServer := &http.Server{
		Addr:              env.Addr,
		Handler:           app,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       5 * time.Minute,
		WriteTimeout:      5 * time.Minute,
		IdleTimeout:       2 * time.Minute,
		MaxHeaderBytes:    1 << 20,
		ErrorLog:          logger,
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	errCh := make(chan error, 1)
	go func() {
		logger.Printf("listening addr=%s dataDir=%s", env.Addr, env.DataDir)
		errCh <- httpServer.ListenAndServe()
	}()

	select {
	case err := <-errCh:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return fmt.Errorf("http server: %w", err)
	case <-ctx.Done():
		logger.Printf("shutdown requested")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(shutdownCtx); err != nil {
			_ = httpServer.Close()
			return fmt.Errorf("graceful shutdown: %w", err)
		}
		err := <-errCh
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			return fmt.Errorf("http server: %w", err)
		}
		logger.Printf("shutdown complete")
		return nil
	}
}

func loadEnvConfig() (envConfig, error) {
	cfg := envConfig{
		Addr:             envOrDefault("REINCHAT_ADDR", ":8080"),
		DataDir:          envOrDefault("REINCHAT_DATA_DIR", "/data"),
		TokenHours:       defaultTokenTTLHours,
		AdminToken:       strings.TrimSpace(os.Getenv("REINCHAT_ADMIN_TOKEN")),
		DSHGatewayURL:    strings.TrimSpace(os.Getenv("REINCHAT_DSH_GATEWAY_URL")),
		DeepSeekToolsKey: strings.TrimSpace(os.Getenv("REINCHAT_DEEPSEEK_TOOLS_KEY")),
	}
	if strings.TrimSpace(cfg.Addr) == "" {
		return envConfig{}, errors.New("REINCHAT_ADDR must not be empty")
	}
	if strings.TrimSpace(cfg.DataDir) == "" {
		return envConfig{}, errors.New("REINCHAT_DATA_DIR must not be empty")
	}

	rawKey := strings.TrimSpace(os.Getenv("REINCHAT_MASTER_KEY"))
	if rawKey == "" {
		return envConfig{}, errors.New("REINCHAT_MASTER_KEY is required (base64-encoded 32 bytes); generate one with: openssl rand -base64 32")
	}
	key, err := base64.StdEncoding.DecodeString(rawKey)
	if err != nil || len(key) != 32 {
		return envConfig{}, errors.New("REINCHAT_MASTER_KEY must be valid base64 encoding exactly 32 bytes")
	}
	cfg.MasterKey = key

	if rawTTL := strings.TrimSpace(os.Getenv("REINCHAT_TOKEN_TTL_HOURS")); rawTTL != "" {
		hours, err := strconv.Atoi(rawTTL)
		if err != nil || hours <= 0 || hours > maxTokenTTLHours {
			return envConfig{}, fmt.Errorf("REINCHAT_TOKEN_TTL_HOURS must be an integer between 1 and %d", maxTokenTTLHours)
		}
		cfg.TokenHours = hours
	}
	return cfg, nil
}

func envOrDefault(name, fallback string) string {
	if value, ok := os.LookupEnv(name); ok {
		return value
	}
	return fallback
}
