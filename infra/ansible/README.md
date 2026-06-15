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
| `node_os` | 커널 sysctl (inotify 등). 값은 `roles/node_os/defaults/main.yml` 에서 관리 |

## 로드맵 (#461)
- [x] Phase 1: `node_os` (sysctl)
- [ ] Phase 2: `container-runtime` (containerd) + kubeadm 사전조건
- [ ] Phase 3: `kubeadm` (init/join)
- [ ] Phase 4: `argocd-bootstrap` (argocd 설치 + root app/project)
