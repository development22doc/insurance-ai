{{/*
================================================================================
ClaimAssist - reusable name & label helpers (foundation, Task 6A-5)
Used across all resource templates for consistent naming and labels.
================================================================================
*/}}

{{/*
Name: short chart/application name.
*/}}
{{- define "claimassist.name" -}}
{{- default .Chart.Name .Values.global.app | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Full name: combined application + release (used for distinct cluster-scoped names).
*/}}
{{- define "claimassist.fullname" -}}
{{- printf "%s-%s" (default .Chart.Name .Values.global.app) .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels for ALL resources (Kubernetes recommended labels + environment).
*/}}
{{- define "claimassist.labels" -}}
app.kubernetes.io/name: {{ include "claimassist.name" . | quote }}
app.kubernetes.io/instance: {{ .Release.Name | quote }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/part-of: {{ .Values.global.app | default .Chart.Name | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
{{- with .Values.global.environment }}
environment: {{ . | quote }}
{{- end }}
{{- end -}}

{{/*
Selector labels (pods). Keep minimal - matches pod template labels.
*/}}
{{- define "claimassist.selectorLabels" -}}
app.kubernetes.io/name: {{ include "claimassist.name" . | quote }}
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}

{{/*
Helper for the chart namespace: prefer the release namespace, else a value.
*/}}
{{- define "claimassist.namespace" -}}
{{- .Release.Namespace | default "claimassist" -}}
{{- end -}}

{{/*
Image reference. Handles optional (empty) registry so the rendered reference is
valid when Docker Hub registry is not yet configured (Task 6A-6: registry placeholder).
Context:
  .root    = chart root context
  .name    = service key (e.g. "customer-service")
  .svc     = the service values map
*/}}
{{- define "claimassist.image" -}}
{{- $root := .root -}}
{{- $svc := .svc -}}
{{- $img := $svc.image | default .name -}}
{{- $registry := $root.Values.image.registry | default "" -}}
{{- /* Per-service immutable image tag. When set (services.<name>.imageTag),
       only that service's pod template changes during a `helm upgrade`, which
       is what enables SERVICE-WISE independent deployment on a single release.
       When absent it falls back to the global image.tag, so untouched services
       keep their already-deployed image and are not rolled out. */ -}}
{{- $tag := $svc.imageTag | default $root.Values.image.tag -}}
{{- /* If the image already contains a slash (includes registry), use it as-is */ -}}
{{- if contains "/" $img -}}
{{- printf "%s:%s" $img $tag -}}
{{- else if $registry -}}
{{- /* Registry set (Kubernetes deploy): <ns>/<service>:<tag>.
       The verified Docker Hub namespace claimassistdev owns the per-service
       repositories directly (e.g. claimassistdev/api-gateway). The old
       intermediate `claimassist` repository segment is intentionally dropped. */ -}}
{{- printf "%s/%s:%s" $registry $img $tag -}}
{{- else -}}
{{- printf "%s/%s:%s" $root.Values.image.repository $img $tag -}}
{{- end -}}
{{- end -}}
