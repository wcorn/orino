# infra

인프라 구성 파일. 모든 변경은 Git을 통해서만 적용한다(GitOps).
`kubectl apply` · `helm install` · 로컬 `terraform apply`를 직접 실행하지 않는다.

| 디렉토리 | 용도 | 적용 경로 |
|---|---|---|
| `argocd/` | ArgoCD Application 정의 (App of Apps). Helm 디렉토리를 가리키는 역할만 | ArgoCD가 자동 동기화 |
| `helm/` | wrapper chart — `Chart.yaml`의 `dependencies`로 upstream 참조, `values.yaml`에 설정 | ArgoCD가 자동 동기화 |
| `terraform/` | AWS 리소스 (Route53, S3 등) | PR = `plan`, main 머지 = 자동 `apply` (GitHub Actions OIDC) |
| `ansible/` | note1/note2 노드 부트스트랩 IaC — 자세한 내용은 [`ansible/README.md`](ansible/README.md) | main 머지 시 자동 apply (tailnet 경유) |

## 검증 (푸시 전)

```bash
yamllint -c .yamllint.yml infra/
helm lint infra/helm/<app>
helm template infra/helm/<app> | kubeconform -strict -ignore-missing-schemas
cd infra/terraform && terraform fmt -check -recursive
cd infra/ansible && ansible-playbook site.yml --syntax-check && ansible-lint
```

Helm 값이 `tpl`을 거쳐 평가되는 설정(예: Alloy River `{{ }}`)은 `helm template` 렌더링 결과를
반드시 눈으로 확인한다.

## Secrets

비밀값은 SealedSecret으로만 커밋한다. 원본은 추적 제외된 루트 `.secrets`에 함께 기록한다.

```bash
ssh note1 "echo -n '<값>' | kubeseal --raw --namespace <ns> --name <secret-name> \
  --controller-name sealed-secrets --controller-namespace kube-system"
```
