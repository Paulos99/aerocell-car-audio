#!/usr/bin/env bash
# Deploy AEROCELL visitor-upload Cloud Function into the aerocell folder.
# Never prints S3 keys or the stand secret.
set -euo pipefail

# Default is the aerocell folder. If access bindings / public invoke are denied there,
# rerun with FOLDER_ID of a folder where this identity is admin (e.g. the default yc folder).
FOLDER_ID="${FOLDER_ID:-b1gm2tlbqeqg6mqvmivf}"
BUCKET="${BUCKET:-aerocell-stand}"
ENDPOINT="${ENDPOINT:-https://storage.yandexcloud.net}"
REGION="${AWS_DEFAULT_REGION:-ru-central1}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://storage.yandexcloud.net/aerocell-stand/}"
PAGE_URL_BASE="${PAGE_URL_BASE:-https://storage.yandexcloud.net/aerocell-stand/u/index.html}"
FUNCTION_NAME="${FUNCTION_NAME:-aerocell-upload}"
SA_NAME="${SA_NAME:-aerocell-upload-runtime}"
LOCKBOX_NAME="${LOCKBOX_NAME:-aerocell-upload-secrets}"
SECRETS_FILE="${SECRETS_FILE:-/home/box/secrets/aerocell-upload.env}"
S3_ENV_FILE="${S3_ENV_FILE:-/home/box/secrets/aerocell-s3.env}"
EXISTING_S3_SA_ID="${EXISTING_S3_SA_ID:-ajeen1miqcvhidojl9pe}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FN_DIR="$ROOT/function"
TMPDIR_DEPLOY="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_DEPLOY"' EXIT

log() { printf '%s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

command -v yc >/dev/null || die "yc CLI is not installed"
yc iam whoami >/dev/null 2>&1 || die "yc is not authenticated"

if ! yc resource-manager folder get --id "$FOLDER_ID" >/dev/null 2>&1; then
  die "no access to folder $FOLDER_ID (aerocell). Need editor/admin on that folder plus functions.editor (or cloud editor)."
fi

json_id() { python3 -c 'import json,sys; t=sys.stdin.read().strip(); print(json.loads(t)["id"] if t.startswith("{") else "")'; }
sa_id="$(yc iam service-account get --name "$SA_NAME" --folder-id "$FOLDER_ID" --format json 2>/dev/null | json_id || true)"
if [[ -z "$sa_id" ]]; then
  log "Creating service account $SA_NAME"
  sa_id="$(yc iam service-account create --name "$SA_NAME" --folder-id "$FOLDER_ID" --description "AEROCELL visitor-upload function runtime" --format json 2>/dev/null | json_id || true)"
fi
if [[ -z "$sa_id" ]]; then
  die "failed to create or find service account $SA_NAME"
fi
log "Service account: $SA_NAME ($sa_id)"

bind_role() {
  local role="$1" subject="$2"
  if yc resource-manager folder list-access-bindings "$FOLDER_ID" --format json | python3 -c "
import json,sys
role='$role'; sid='$subject'
for b in json.load(sys.stdin):
    sub=b.get('subject') or {}
    if (b.get('roleId') or b.get('role_id'))==role and sub.get('id')==sid:
        sys.exit(0)
sys.exit(1)
"; then
    return 0
  fi
  if yc resource-manager folder add-access-binding "$FOLDER_ID" --role "$role" --service-account-id "$subject" >/dev/null; then
    log "Granted $role"
    return 0
  fi
  log "WARNING: cannot grant $role on folder $FOLDER_ID (need folder admin or a binding grant)."
  return 1
}

bind_role storage.editor "$sa_id" || true
bind_role lockbox.payloadViewer "$sa_id" || true
bind_role functions.functionInvoker "$sa_id" || true

# Reuse stand secret if already issued, otherwise generate one.
mkdir -p "$(dirname "$SECRETS_FILE")"
umask 077
existing_secret=""
if [[ -f "$SECRETS_FILE" ]]; then
  existing_secret="$(python3 -c '
import pathlib
vals={}
for line in pathlib.Path("'"$SECRETS_FILE"'").read_text().splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k,v=line.split("=",1)
        vals[k.strip()]=v.strip().strip("\"")
print(vals.get("STAND_SECRET",""))
')"
fi
if [[ -z "$existing_secret" ]]; then
  existing_secret="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
fi
stand_secret="$existing_secret"

# Access keys for the function SA (preferred). Fall back to existing bucket keys if create is denied.
access_id=""
access_secret=""
if key_json="$(yc iam access-key create --service-account-id "$sa_id" --description "aerocell-upload function S3" --format json 2>"$TMPDIR_DEPLOY/access-key.err")"; then
  python3 -c '
import json,pathlib,sys
data=json.loads(sys.argv[1])
# yc json: {"access_key":{"key_id":"..."}, "secret":"..."}
key_id=(data.get("access_key") or data).get("key_id") or (data.get("access_key") or {}).get("id") or data.get("key_id") or ""
secret=data.get("secret") or (data.get("access_key") or {}).get("secret") or ""
pathlib.Path(sys.argv[2]).write_text(key_id+"\n")
pathlib.Path(sys.argv[3]).write_text(secret+"\n")
if not key_id or not secret:
    sys.exit(2)
' "$key_json" "$TMPDIR_DEPLOY/key_id" "$TMPDIR_DEPLOY/key_secret"
  access_id="$(cat "$TMPDIR_DEPLOY/key_id")"
  access_secret="$(cat "$TMPDIR_DEPLOY/key_secret")"
  log "Created static access key for $SA_NAME"
else
  log "Could not create a static key for $SA_NAME; will reuse existing bucket keys from $S3_ENV_FILE"
  if [[ ! -f "$S3_ENV_FILE" ]]; then
    die "missing $S3_ENV_FILE and cannot create access keys. Grant iam.serviceAccounts.accessKeyAdmin on folder $FOLDER_ID."
  fi
  python3 -c '
import pathlib,sys
vals={}
for line in pathlib.Path(sys.argv[1]).read_text().splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k,v=line.split("=",1)
        vals[k.strip()]=v.strip().strip("\"").strip("'\''")
pathlib.Path(sys.argv[2]).write_text(vals.get("AWS_ACCESS_KEY_ID",""))
pathlib.Path(sys.argv[3]).write_text(vals.get("AWS_SECRET_ACCESS_KEY",""))
if not vals.get("AWS_ACCESS_KEY_ID") or not vals.get("AWS_SECRET_ACCESS_KEY"):
    sys.exit(2)
' "$S3_ENV_FILE" "$TMPDIR_DEPLOY/key_id" "$TMPDIR_DEPLOY/key_secret"
  access_id="$(cat "$TMPDIR_DEPLOY/key_id")"
  access_secret="$(cat "$TMPDIR_DEPLOY/key_secret")"
fi

# Lockbox secret (create or add version). Payload never echoed.
python3 -c '
import json,pathlib,sys
payload=[
  {"key":"AWS_ACCESS_KEY_ID","text_value":pathlib.Path(sys.argv[1]).read_text().strip()},
  {"key":"AWS_SECRET_ACCESS_KEY","text_value":pathlib.Path(sys.argv[2]).read_text().strip()},
  {"key":"STAND_SECRET","text_value":sys.argv[3]},
]
pathlib.Path(sys.argv[4]).write_text(json.dumps(payload))
' "$TMPDIR_DEPLOY/key_id" "$TMPDIR_DEPLOY/key_secret" "$stand_secret" "$TMPDIR_DEPLOY/lockbox.json"

lockbox_id="$(yc lockbox secret get --name "$LOCKBOX_NAME" --folder-id "$FOLDER_ID" --format json 2>/dev/null | json_id || true)"
if [[ -z "$lockbox_id" ]]; then
  log "Creating Lockbox secret $LOCKBOX_NAME"
  lockbox_id="$(yc lockbox secret create --name "$LOCKBOX_NAME" --folder-id "$FOLDER_ID" --payload - --format json < "$TMPDIR_DEPLOY/lockbox.json" | json_id)"
else
  log "Adding new Lockbox version"
  yc lockbox secret add-version --id "$lockbox_id" --payload - < "$TMPDIR_DEPLOY/lockbox.json" >/dev/null
fi
log "Lockbox: $LOCKBOX_NAME ($lockbox_id)"

fn_id="$(yc serverless function get --name "$FUNCTION_NAME" --folder-id "$FOLDER_ID" --format json 2>/dev/null | json_id || true)"
if [[ -z "$fn_id" ]]; then
  log "Creating function $FUNCTION_NAME"
  fn_id="$(yc serverless function create --name "$FUNCTION_NAME" --folder-id "$FOLDER_ID" --description "AEROCELL visitor track upload" --format json | json_id)"
fi
function_url="https://functions.yandexcloud.net/${fn_id}"
log "Function: $FUNCTION_NAME ($fn_id)"

# Bake function URL into the page copy that gets zipped with the version.
STAGE="$TMPDIR_DEPLOY/src"
mkdir -p "$STAGE"
cp "$FN_DIR/index.py" "$FN_DIR/requirements.txt" "$STAGE/"
python3 -c '
from pathlib import Path
import sys
html=Path(sys.argv[1]).read_text(encoding="utf-8")
html=html.replace("__FUNCTION_BASE_URL__", sys.argv[2].rstrip("/"))
Path(sys.argv[3]).write_text(html, encoding="utf-8")
' "$FN_DIR/page.html" "$function_url" "$STAGE/page.html"

log "Publishing function version"
yc serverless function version create \
  --function-id "$fn_id" \
  --runtime python312 \
  --entrypoint index.handler \
  --memory 256m \
  --execution-timeout 30s \
  --source-path "$STAGE" \
  --service-account-id "$sa_id" \
  --environment "BUCKET=$BUCKET,ENDPOINT=$ENDPOINT,AWS_DEFAULT_REGION=$REGION,PUBLIC_BASE_URL=$PUBLIC_BASE_URL,PAGE_URL_BASE=$PAGE_URL_BASE,FUNCTION_BASE_URL=$function_url,MAX_BYTES=52428800,UPLOAD_URL_TTL_SEC=600,SESSION_TTL_SEC=1800" \
  --secret "id=$lockbox_id,key=AWS_ACCESS_KEY_ID,environment-variable=AWS_ACCESS_KEY_ID" \
  --secret "id=$lockbox_id,key=AWS_SECRET_ACCESS_KEY,environment-variable=AWS_SECRET_ACCESS_KEY" \
  --secret "id=$lockbox_id,key=STAND_SECRET,environment-variable=STAND_SECRET" \
  --format json >/dev/null

if yc serverless function allow-unauthenticated-invoke --id "$fn_id" >/dev/null; then
  log "Public invoke allowed"
else
  log "WARNING: cannot allow unauthenticated invoke on $fn_id. The function may be unreachable from a phone. Need functions.admin or folder admin on $FOLDER_ID."
fi

# CORS (browser PUT may use path-style on the same host, but complete/upload-url are cross-origin)
# Apply via S3 API using the keys we just issued (or existing). Do not print keys.
python3 -c '
import sys
from pathlib import Path
import boto3
from botocore.config import Config
from botocore.exceptions import ClientError
bucket=sys.argv[1]; endpoint=sys.argv[2]; region=sys.argv[3]
key=Path(sys.argv[4]).read_text().strip(); secret=Path(sys.argv[5]).read_text().strip()
s3=boto3.client("s3", aws_access_key_id=key, aws_secret_access_key=secret,
    endpoint_url=endpoint, region_name=region,
    config=Config(signature_version="s3v4"))
try:
    s3.put_bucket_cors(Bucket=bucket, CORSConfiguration={"CORSRules":[{
        "AllowedHeaders":["*"],
        "AllowedMethods":["GET","PUT","HEAD","POST"],
        "AllowedOrigins":["*"],
        "ExposeHeaders":["ETag","Content-Length"],
        "MaxAgeSeconds":3600,
    }]})
    print("CORS ok", file=sys.stderr)
except Exception as e:
    print("CORS not applied:", type(e).__name__, file=sys.stderr)
'

# Lifecycle via yc (does not require S3 PutBucketLifecycle if IAM can update the bucket)
if yc storage bucket update --name "$BUCKET" --lifecycle-rules-from-file "$ROOT/tools/lifecycle-uploads.json" >/dev/null; then
  log "Lifecycle rule set: prefix uploads/, expire after 1 day"
else
  log "WARNING: could not set lifecycle rule on $BUCKET"
fi

# Public page in the bucket
python3 -c '
import sys
from pathlib import Path
import boto3
from botocore.config import Config
bucket=sys.argv[1]; endpoint=sys.argv[2]; region=sys.argv[3]
key=Path(sys.argv[4]).read_text().strip(); secret=Path(sys.argv[5]).read_text().strip()
body=Path(sys.argv[6]).read_bytes()
s3=boto3.client("s3", aws_access_key_id=key, aws_secret_access_key=secret,
    endpoint_url=endpoint, region_name=region,
    config=Config(signature_version="s3v4"))
s3.put_object(Bucket=bucket, Key="u/index.html", Body=body,
    ContentType="text/html; charset=utf-8", ACL="public-read", CacheControl="no-cache")
print("Uploaded public page u/index.html", file=sys.stderr)
' "$BUCKET" "$ENDPOINT" "$REGION" "$TMPDIR_DEPLOY/key_id" "$TMPDIR_DEPLOY/key_secret" "$STAGE/page.html"

# Deny anonymous read of uploads/* while keeping the rest of the public bucket as-is.
# Try a NotPrincipal policy for the function SA (+ existing aerocell-s3 SA).
python3 -c '
import json,sys
from pathlib import Path
ids=[sys.argv[1], sys.argv[2]]
ids=[i for i in ids if i]
policy={
  "Version":"2012-10-17",
  "Statement":[
    {
      "Sid":"DenyAnonymousAndOthersOnUploads",
      "Effect":"Deny",
      "NotPrincipal":{"CanonicalUser": ids if len(ids)>1 else ids[0]},
      "Action":["s3:GetObject","s3:GetObjectVersion"],
      "Resource":"arn:aws:s3:::aerocell-stand/uploads/*"
    }
  ]
}
Path(sys.argv[3]).write_text(json.dumps(policy, indent=2))
' "$sa_id" "$EXISTING_S3_SA_ID" "$TMPDIR_DEPLOY/policy.json"
if yc storage bucket update --name "$BUCKET" --policy-from-file "$TMPDIR_DEPLOY/policy.json" >/dev/null; then
  log "Bucket policy applied: deny public read on uploads/"
else
  log "WARNING: could not apply bucket policy for uploads/. Objects under uploads/ may still be world-readable because the bucket has anonymous read. Presigned URLs still work; UUID keys reduce guessing risk."
fi

# Persist URLs + stand secret for the Android build (mode 600). No S3 keys here.
python3 -c '
from pathlib import Path
import sys
path=Path(sys.argv[1])
vals={"FUNCTION_ID":sys.argv[2],"FUNCTION_URL":sys.argv[3],"STAND_SECRET":sys.argv[4],
      "PAGE_URL_BASE":sys.argv[5],"FOLDER_ID":sys.argv[6],"FUNCTION_NAME":sys.argv[7],
      "SA_ID":sys.argv[8],"LOCKBOX_ID":sys.argv[9]}
# keep unknown previous keys except ones we overwrite
prev={}
if path.exists():
    for line in path.read_text().splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k,v=line.split("=",1)
            prev[k.strip()]=v.strip()
prev.update(vals)
text="\n".join(f"{k}={prev[k]}" for k in prev)+"\n"
path.write_text(text)
path.chmod(0o600)
' "$SECRETS_FILE" "$fn_id" "$function_url" "$stand_secret" "$PAGE_URL_BASE" "$FOLDER_ID" "$FUNCTION_NAME" "$sa_id" "$lockbox_id"

# Also write a gradle properties file next to the package if a destination is given.
PROPS_OUT="${UPLOAD_CLOUD_PROPERTIES:-$ROOT/../repo/upload-cloud.properties}"
if [[ -n "${PROPS_OUT}" ]]; then
  python3 -c '
from pathlib import Path
import sys
p=Path(sys.argv[1])
p.write_text(
    "UPLOAD_FUNCTION_URL="+sys.argv[2]+"\n"
    "UPLOAD_STAND_SECRET="+sys.argv[3]+"\n"
    "UPLOAD_USE_CLOUD=true\n"
)
p.chmod(0o600)
' "$PROPS_OUT" "$function_url" "$stand_secret"
  log "Wrote Android properties to $PROPS_OUT (gitignored, mode 600)"
fi

# Wipe key material from the temp dir explicitly (trap also removes the dir).
rm -f "$TMPDIR_DEPLOY/key_id" "$TMPDIR_DEPLOY/key_secret" "$TMPDIR_DEPLOY/lockbox.json" "$TMPDIR_DEPLOY/access-key.err"

log ""
log "Deployed $FUNCTION_NAME"
log "Function URL: $function_url"
log "Page URL:     $PAGE_URL_BASE?t=<token>"
log "Health:       $function_url/health"
log "Secrets file: $SECRETS_FILE (not printed)"
