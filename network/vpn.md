# VPN

## PN

private network는 외부에서 접근 불가능한 제한된 범위의 ip 주소 공간을 이용하는 네트워크이다

통상적으로 이 제한된 공간에 접근하기 위해선 동일 ip 주소 대역을 할당받아 접근해야하며, LAN, WAN과 같은 물리장비로 접근할 수 있다

하지만 먼 지역, 해외같은 경우에는 현실적으로 이런 물리장비가 이어질 수 없어 소프트웨어로 사내 네트워크 ip를 할당받는 방법이 VPN이라고 한다

## VPN

vpn은 사내 네트워크의 ip를 할당받고, 연결시켜주는 소프트웨어이다

vpn client라는 소프트웨어를 이용하여 사내 네트워크의 ip를 할당받고, Secure Gateway와 터널링을 구성하여 물리 장비 없이 네트워크 통신을 수행할 수 있다

## VPN 동작

* 공유기 ip: 9.9.9.10
* Secure Gateway ip: 3.3.3.1
* vpn client ip: 3.3.3.200
* 사내 서버 ip: 3.3.3.20

1. 네트워크 패킷에 inner ip header가 붙고, 다음과 같은 정보를 가지게 된다

    * source: vpn client로 할당받은 ip (3.3.3.200)
    * dest: 사내 네트워크 내의 컴퓨터 ip (3.3.3.20)

2. inner ip header는 private network에 접근하기 위한 ip 정보일뿐 실제 인터넷으로 가기 위한 정보가 아니다. 따라서 outer ip header가 그 앞에 붙어 다음의 정보를 가지게 된다

    * source: 공유기 ip (9.9.9.10)
    * dest: Secure Gateway ip (3.3.3.1)

3. 게이트웨이에서는 outer를 떼고, inner ip header를 복호화하여 private network 내의 어떤 목적지로 가는지 확인하고, 라우팅을 한다

4. 응답은 이를 역으로 한다

---

## VPN 유형

| 유형 | 설명 | 사용 사례 |
|------|------|----------|
| **Remote Access VPN** | 개인이 VPN 클라이언트로 사내 네트워크에 접속 | 재택근무, 외부 출장 |
| **Site-to-Site VPN** | 두 네트워크(사무실)를 항상 연결 | 본사-지사 연결 |
| **SSL VPN** | 웹 브라우저로 접속 가능 (별도 클라이언트 불필요) | 간편한 원격 접속 |

## VPN 프로토콜

| 프로토콜 | 암호화 | 속도 | 특징 |
|---------|--------|------|------|
| **IPSec** | 강력 (AES) | 중간 | Site-to-Site에 주로 사용 |
| **OpenVPN** | 강력 (TLS) | 중간 | 오픈소스, TCP/UDP 모두 지원 |
| **WireGuard** | 강력 (ChaCha20) | 빠름 | 최신 프로토콜, 코드 간결, 낮은 지연 |
| **L2TP/IPSec** | IPSec 의존 | 느림 | L2 터널링 + IPSec 암호화 조합 |

## Split Tunneling

```
                      ┌── 사내 서버(3.3.3.x) → VPN 터널 경유
VPN Client ───────────┤
                      └── 인터넷(google.com 등) → 직접 접속
```

* **Full Tunneling**: 모든 트래픽이 VPN을 경유 → 보안 강화, 속도 저하
* **Split Tunneling**: 사내 트래픽만 VPN 경유, 나머지는 직접 → 속도 유지, 보안 일부 타협
