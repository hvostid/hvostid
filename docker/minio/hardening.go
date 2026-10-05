// SPDX-License-Identifier: AGPL-3.0-only
// Local restrictions for the standalone object store used by HvostID.
package cmd

import (
	"errors"
	"mime"
	"net/http"
	"strings"
)

const hvostidAllowReplication = false

var errHvostidUnsupportedFeature = errors.New("feature disabled in the HvostID standalone storage build")

func hvostidForbiddenReplicationKey(key string) bool {
	key = strings.ToLower(key)
	return strings.HasPrefix(key, "x-minio-replication-") || key == "x-minio-source-replication-request"
}

// Reject unsafe optional protocols before authentication, body parsing, or routing.
// These guards implement the upstream advisory workarounds listed in README.md.
func hvostidSecurityHandler(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		blocked := false
		// POST-policy forms can carry replication metadata in their body. This
		// application uses PUT/presigned PUT and the separate S3 multipart API.
		for _, contentType := range r.Header.Values("Content-Type") {
			mediaType, _, err := mime.ParseMediaType(contentType)
			if r.Method == http.MethodPost && err == nil && strings.EqualFold(mediaType, "multipart/form-data") {
				blocked = true
			}
		}
		for key, values := range r.Header {
			key = strings.ToLower(key)
			if hvostidForbiddenReplicationKey(key) || key == "x-amz-security-token" {
				blocked = true
			}
			if key == "x-amz-content-sha256" {
				for _, value := range values {
					if strings.EqualFold(strings.TrimSpace(value), "STREAMING-UNSIGNED-PAYLOAD-TRAILER") {
						blocked = true
					}
				}
			}
		}
		for key, values := range r.URL.Query() {
			key = strings.ToLower(key)
			if key == "select" || strings.HasPrefix(key, "replication") || key == "x-amz-security-token" || hvostidForbiddenReplicationKey(key) {
				blocked = true
			}
			if key == "x-amz-content-sha256" {
				for _, value := range values {
					if strings.EqualFold(strings.TrimSpace(value), "STREAMING-UNSIGNED-PAYLOAD-TRAILER") {
						blocked = true
					}
				}
			}
		}
		path := strings.ToLower(r.URL.Path)
		if strings.HasPrefix(path, "/minio/storage/") ||
			(strings.HasPrefix(path, "/minio/admin/") &&
				(strings.Contains(path, "site-replication") || strings.Contains(path, "bucket-target") || strings.Contains(path, "remote-target") || strings.Contains(path, "start-job"))) {
			blocked = true
		}
		if blocked {
			w.Header().Set("Content-Type", "application/xml")
			w.Header().Set("X-Hvostid-Storage-Guard", "disabled-feature")
			w.WriteHeader(http.StatusNotImplemented)
			_, _ = w.Write([]byte("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>NotImplemented</Code><Message>This optional storage protocol is disabled.</Message></Error>"))
			return
		}
		next.ServeHTTP(w, r)
	})
}
