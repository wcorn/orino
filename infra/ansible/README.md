# Ansible — 노드 부트스트랩 IaC

note1/note2 의 **OS 설치를 제외한 설정을 코드로** 관리한다 (#461).
ArgoCD(워크로드) 아래 레이어 — 노드 OS · 클러스터 · ArgoCD 부트스트랩을 점진적으로 코드화.

## 실행
```bash
cd infra/ansible
ansible-playbook site.yml --ask-become-pass     # sudo 비밀번호 입력
# 특정 노드만: ansible-playbook site.yml --limit note2 --ask-become-pass
```

- 접속: inventory(`note1`=192.168.0.18, `note2`=192.168.0.8), user `dongseok`, key `~/.ssh/home.pem`
- **idempotent** — 여러 번 실행해도 안전, 새 노드 조인 시에도 동일 명령
- 비밀번호/키는 커밋하지 않는다(런타임 입력 또는 ansible-vault)

## 롤
| 롤 | 내용 |
|---|---|
| `node_os` | 커널 sysctl (inotify 등). 값은 `roles/node_os/defaults/main.yml` |
| `kubeadm_prereqs` | 커널 모듈(overlay/br_netfilter) + 네트워크 sysctl + swap off |
| `container_runtime` | containerd 서비스 보장 (config 무중단 위해 미변경) |
| `kubeadm` | 클러스터 init/join + Calico CNI — 현 클러스터 역공학, **신규 노드 전용** |
| `argocd_bootstrap` | ArgoCD helm 설치 + AppProject/root(App-of-Apps) apply — **미설치 시에만** |

### ⚠️ `kubeadm` 롤 안전·한계
- **기존 멤버 노드(`/etc/kubernetes/kubelet.conf` 존재)에는 전부 skip** — 멤버십 가드로
  init/join 이 운영 노드에 재실행되지 않는다. note1/note2 적용 시 changed=0(무중단).
- **신규 노드에서만** 저장소·패키지·init/join·Calico 설치 경로가 돈다.
- init/join 경로는 운영 클러스터가 하나뿐이라 **실증 테스트 불가**(역공학). 값은
  `roles/kubeadm/defaults/main.yml` 에 현 노드 그대로 핀(k8s 1.34.5, podSubnet 10.244.0.0/16,
  Calico v3.31.4). 진짜 새 노드 부트스트랩 시 검증 필요.
- Calico 는 GitOps 미관리라 이 롤이 manifest 로 설치(podSubnet 에 맞춰 CIDR 치환).

### ⚠️ `argocd_bootstrap` 롤 안전·한계
- **이미 설치돼 있으면(`argocd-server` 디플로이 존재) 전체 skip** — 운영 ArgoCD 에 helm 재설치/재apply 안 함. note1 적용 시 조회만 돌고 changed=0(무중단).
- ArgoCD 본체는 chicken-and-egg 라 GitOps 밖 → 이 롤이 helm(argo-cd v9.4.10)으로 최초 설치 후 AppProject(orino) → root(App-of-Apps) 순서로 apply. 그 뒤는 ArgoCD 가 GitOps 로 가져간다.
- values/project/root 소스는 항상 **repo main** raw URL. 단일 클러스터라 실증 테스트 불가(역공학).

## 로드맵 (#461) — 완료
- [x] Phase 1: `node_os` (sysctl)
- [x] Phase 2: `container_runtime` (containerd) + kubeadm 사전조건(`kubeadm_prereqs`)
- [x] Phase 3: `kubeadm` (init/join + Calico)
- [x] Phase 4: `argocd_bootstrap` (argocd 설치 + root app/project)

→ **OS 설치를 제외한 노드~ArgoCD 부트스트랩 전 구간이 코드화**됐다. 새 노드는 `ansible-playbook site.yml` 한 번으로 클러스터 조인까지(신규 control-plane이면 ArgoCD 부트스트랩까지) 재현된다.
