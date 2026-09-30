// SPDX-License-Identifier: AGPL-3.0-only
// Exact-match patches fail closed when the pinned upstream source changes.
package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func replace(root, file, before, after string) {
	path := filepath.Join(root, file)
	contents, err := os.ReadFile(path)
	if err != nil {
		panic(err)
	}
	text := strings.ReplaceAll(string(contents), "\r\n", "\n")
	if strings.Count(text, before) != 1 {
		panic(fmt.Sprintf("upstream patch anchor changed in %s", file))
	}
	if err := os.WriteFile(path, []byte(strings.Replace(text, before, after, 1)), 0644); err != nil {
		panic(err)
	}
}

func main() {
	if len(os.Args) != 2 {
		panic("usage: apply-patches <minio-source>")
	}
	root := os.Args[1]
	anchor := "func serverHandleCmdArgs(ctxt serverCtxt) {"
	replace(root, "cmd/server-main.go", anchor, anchor+"\n\tif len(ctxt.FTP) > 0 || len(ctxt.SFTP) > 0 { logger.FatalIf(errHvostidUnsupportedFeature, \"FTP and SFTP are disabled in the HvostID standalone storage build\") }")
	replace(root, "cmd/routers.go", "\t\tregisterDistErasureRouters(router, endpointServerPools)", "\t\treturn nil, errHvostidUnsupportedFeature")
	replace(root, "cmd/routers.go", "\treturn router, nil", "\treturn hvostidSecurityHandler(router), nil")
	replace(root, "cmd/routers.go", "\tregisterSTSRouter(router)", "\t// External identity providers and STS are disabled in this standalone build.")
	replace(root, "cmd/api-router.go", "HandlerFunc(s3APIMiddleware(api.SelectObjectContentHandler, traceHdrsS3HFlag))", "HandlerFunc(s3APIMiddleware(notImplementedHandler))")
	replace(root, "cmd/api-router.go", "HandlerFunc(s3APIMiddleware(api.PostPolicyBucketHandler, traceHdrsS3HFlag))", "HandlerFunc(s3APIMiddleware(notImplementedHandler))")
	anchor = "func extractMetadataFromMime(ctx context.Context, v textproto.MIMEHeader, m map[string]string) error {"
	replace(root, "cmd/handler-utils.go", anchor, anchor+"\n\tfor key := range v { if hvostidForbiddenReplicationKey(key) { return errHvostidUnsupportedFeature } }")
	anchor = "func checkClaimsFromToken(r *http.Request, cred auth.Credentials) (map[string]any, APIErrorCode) {"
	replace(root, "cmd/auth-handler.go", anchor, anchor+"\n\tif cred.IsTemp() && !cred.IsServiceAccount() { return nil, ErrInvalidToken }")
	// Existing on-disk replication configuration must not restart background replication.
	anchor = "func (sys *BucketMetadataSys) GetReplicationConfig(ctx context.Context, bucket string) (*replication.Config, time.Time, error) {"
	replace(root, "cmd/bucket-metadata-sys.go", anchor, anchor+"\n\tif !hvostidAllowReplication { return nil, time.Time{}, BucketReplicationConfigNotFound{Bucket: bucket} }")
	anchor = "func (c *SiteReplicationSys) Init(ctx context.Context, objAPI ObjectLayer) error {"
	replace(root, "cmd/site-replication.go", anchor, anchor+"\n\tif !hvostidAllowReplication { return nil }")
	anchor = "func (r *BatchJobReplicateV1) StartFromSource(ctx context.Context, api ObjectLayer, job BatchJobRequest) error {"
	replace(root, "cmd/batch-handlers.go", anchor, anchor+"\n\tif !hvostidAllowReplication { return errHvostidUnsupportedFeature }")
	anchor = "func (r *BatchJobReplicateV1) Start(ctx context.Context, api ObjectLayer, job BatchJobRequest) error {"
	replace(root, "cmd/batch-handlers.go", anchor, anchor+"\n\tif !hvostidAllowReplication { return errHvostidUnsupportedFeature }")
	anchor = "func (sys *BucketTargetSys) SetTarget(ctx context.Context, bucket string, tgt *madmin.BucketTarget, update bool) error {"
	replace(root, "cmd/bucket-targets.go", anchor, anchor+"\n\tif !hvostidAllowReplication { return errHvostidUnsupportedFeature }")
	anchor = "func (sys *BucketTargetSys) set(bucket string, meta BucketMetadata) {"
	replace(root, "cmd/bucket-targets.go", anchor, anchor+"\n\tif !hvostidAllowReplication { return }")
	anchor = "func (sys *BucketTargetSys) UpdateAllTargets(bucket string, tgts *madmin.BucketTargets) {"
	replace(root, "cmd/bucket-targets.go", anchor, anchor+"\n\tif !hvostidAllowReplication { return }")
	anchor = "\t// reload healthCheck endpoints map periodically to remove stale endpoints from the map."
	replace(root, "cmd/bucket-targets.go", anchor, "\tif !hvostidAllowReplication { return sys }\n"+anchor)
}
