// SPDX-License-Identifier: AGPL-3.0-only
package cmd

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestHvostidSecurityGuards(t *testing.T) {
	tests := []struct {
		name, target string
		headers      http.Header
	}{
		{"unsigned header", "/bucket/object", http.Header{"X-Amz-Content-Sha256": {"STREAMING-UNSIGNED-PAYLOAD-TRAILER"}}},
		{"snowball", "/bucket/object", http.Header{"X-Amz-Content-Sha256": {"STREAMING-UNSIGNED-PAYLOAD-TRAILER"}, "X-Amz-Meta-Snowball-Auto-Extract": {"true"}}},
		{"duplicate header", "/bucket/object", http.Header{"X-Amz-Content-Sha256": {"UNSIGNED-PAYLOAD", "STREAMING-UNSIGNED-PAYLOAD-TRAILER"}}},
		{"unsigned query", "/bucket/object?X-Amz-Content-Sha256=STREAMING-UNSIGNED-PAYLOAD-TRAILER", nil},
		{"legacy STS header", "/bucket/object", http.Header{"X-Amz-Security-Token": {"legacy-token"}}},
		{"legacy STS query", "/bucket/object?X-Amz-Security-Token=legacy-token", nil},
		{"encoded select", "/bucket/object?%73elect&select-type=2", nil},
		{"replication config", "/bucket?replication", nil},
		{"replication SSE injection", "/bucket/object", http.Header{"X-Minio-Replication-Server-Side-Encryption-Iv": {"bad"}}},
		{"replication query injection", "/bucket/object?X-Minio-Replication-Server-Side-Encryption-Iv=bad", nil},
		{"replication flag", "/bucket/object", http.Header{"X-Minio-Source-Replication-Request": {"true"}}},
		{"site replication", "/minio/admin/v3/site-replication/add", nil},
		{"batch replication", "/minio/admin/v3/start-job", nil},
		{"remote target creation", "/minio/admin/v3/set-remote-target", nil},
		{"remote target listing", "/minio/admin/v3/list-remote-targets", nil},
		{"remote target removal", "/minio/admin/v3/remove-remote-target", nil},
		{"internode storage", "/minio/storage/data/v63/rmpl", nil},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			request := httptest.NewRequest(http.MethodPut, tt.target, strings.NewReader("untrusted body"))
			if tt.headers != nil {
				request.Header = tt.headers
			}
			response := httptest.NewRecorder()
			hvostidSecurityHandler(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { t.Fatal("unsafe request reached upstream handler") })).ServeHTTP(response, request)
			if response.Code != http.StatusNotImplemented || response.Header().Get("X-Hvostid-Storage-Guard") != "disabled-feature" {
				t.Fatalf("guard did not reject: %d %s", response.Code, response.Body.String())
			}
		})
	}
}

func TestHvostidPostPolicyFormsAreDisabled(t *testing.T) {
	for _, contentType := range []string{"multipart/form-data; boundary=example", "Multipart/Form-Data; boundary=example"} {
		request := httptest.NewRequest(http.MethodPost, "/bucket", strings.NewReader("untrusted form"))
		request.Header.Set("Content-Type", contentType)
		response := httptest.NewRecorder()
		hvostidSecurityHandler(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { t.Fatal("POST-policy form reached upstream") })).ServeHTTP(response, request)
		if response.Code != http.StatusNotImplemented {
			t.Fatalf("form was not rejected: %d", response.Code)
		}
	}
}

func TestHvostidOrdinaryS3RequestsRemainAvailable(t *testing.T) {
	for _, target := range []string{"/bucket", "/bucket/object", "/bucket/object?uploads", "/bucket/object?uploadId=id&partNumber=1", "/bucket/object?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=signature", "/minio/health/live"} {
		request := httptest.NewRequest(http.MethodPut, target, nil)
		request.Header.Set("X-Amz-Content-Sha256", "UNSIGNED-PAYLOAD")
		response := httptest.NewRecorder()
		hvostidSecurityHandler(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })).ServeHTTP(response, request)
		if response.Code != http.StatusNoContent {
			t.Fatalf("ordinary S3 request rejected: %s", target)
		}
	}
}
