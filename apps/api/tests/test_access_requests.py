from datetime import datetime, timezone
from types import SimpleNamespace
from uuid import UUID, uuid4

from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.api.routes import accounting as accounting_routes
from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.main import app
from app.models.accounting import AccessRequestStatus


OWNER_ID = UUID("10000000-0000-0000-0000-000000000001")
REQUESTER_ID = UUID("20000000-0000-0000-0000-000000000002")


class FakeAccountingRepository:
    company_id = uuid4()
    request_id = uuid4()
    approved_members: set[UUID] = set()
    rejected = False

    def __init__(self, db: object) -> None:
        self.db = db

    def create_company(
        self,
        user_id: UUID,
        email: str | None,
        full_name: str | None,
        legal_name: str,
        trade_name: str | None = None,
        gstin: str | None = None,
        state_code: str | None = None,
    ) -> SimpleNamespace:
        self.company_id = FakeAccountingRepository.company_id
        return SimpleNamespace(
            id=FakeAccountingRepository.company_id,
            legal_name=legal_name,
            trade_name=trade_name,
            gstin=gstin,
            state_code=state_code,
        )

    def request_company_access(
        self,
        company_id: UUID,
        requester_id: UUID,
        requester_email: str | None,
        requested_role: str,
        full_name: str | None = None,
    ) -> SimpleNamespace:
        return access_request(company_id, requester_id, requester_email, requested_role)

    def list_access_requests(self, company_id: UUID, owner_id: UUID) -> list[SimpleNamespace]:
        if owner_id != OWNER_ID:
            raise PermissionError("Only the company owner can approve access requests.")
        return [access_request(company_id, REQUESTER_ID, "requester@abhay.test", "accountant")]

    def decide_access_request(
        self,
        company_id: UUID,
        request_id: UUID,
        owner_id: UUID,
        decision: str,
        role_code: str,
    ) -> SimpleNamespace:
        if owner_id != OWNER_ID:
            raise PermissionError("Only the company owner can approve access requests.")
        request = access_request(company_id, REQUESTER_ID, "requester@abhay.test", role_code)
        if decision == "approve":
            FakeAccountingRepository.approved_members.add(REQUESTER_ID)
            request.status = AccessRequestStatus.approved
        else:
            FakeAccountingRepository.rejected = True
            request.status = AccessRequestStatus.rejected
        request.decided_at = datetime.now(timezone.utc)
        return request

    def ensure_member(self, company_id: UUID, profile_id: UUID) -> None:
        if profile_id != OWNER_ID and profile_id not in FakeAccountingRepository.approved_members:
            raise PermissionError("You do not have access to this company.")

    def list_audit_events(self, company_id: UUID, limit: int = 20) -> list[SimpleNamespace]:
        return [
            SimpleNamespace(
                id=uuid4(),
                company_id=company_id,
                voucher_id=uuid4(),
                actor_id=OWNER_ID,
                event_type="voucher.posted",
                event_payload={"voucher_number": "PAYMENT-000001"},
                created_at=datetime.now(timezone.utc),
            )
        ]


def access_request(
    company_id: UUID,
    requester_id: UUID,
    requester_email: str | None,
    requested_role: str,
) -> SimpleNamespace:
    return SimpleNamespace(
        id=FakeAccountingRepository.request_id,
        company_id=company_id,
        company=SimpleNamespace(legal_name="Owner Company"),
        requester_profile_id=requester_id,
        requester_email=requester_email,
        requested_role=requested_role,
        status=AccessRequestStatus.pending,
        created_at=datetime.now(timezone.utc),
        decided_at=None,
    )


def override_user(user_id: UUID, email: str) -> AuthenticatedUser:
    return AuthenticatedUser(
        id=str(user_id),
        email=email,
        role="authenticated",
        claims={"sub": str(user_id), "email": email, "user_metadata": {"full_name": email}},
    )


def client_for(user: AuthenticatedUser, monkeypatch) -> TestClient:
    monkeypatch.setattr(accounting_routes, "AccountingRepository", FakeAccountingRepository)
    app.dependency_overrides[require_user] = lambda: user
    app.dependency_overrides[get_db] = lambda: object()
    return TestClient(app)


def teardown_function() -> None:
    app.dependency_overrides.clear()
    FakeAccountingRepository.approved_members.clear()
    FakeAccountingRepository.rejected = False


def test_company_creation_assigns_current_user_as_owner(monkeypatch) -> None:
    client = client_for(override_user(OWNER_ID, "owner@abhay.test"), monkeypatch)

    response = client.post(
        "/companies",
        json={
            "legal_name": "Launch Company Private Limited",
            "trade_name": "Launch Company",
            "state_code": "27",
        },
        headers={"Authorization": "Bearer test"},
    )

    assert response.status_code == 200
    assert response.json()["legal_name"] == "Launch Company Private Limited"
    assert response.json()["state_code"] == "27"


def test_access_request_and_owner_approval_grants_company_access(monkeypatch) -> None:
    company_id = FakeAccountingRepository.company_id
    requester = client_for(override_user(REQUESTER_ID, "requester@abhay.test"), monkeypatch)

    request_response = requester.post(
        f"/companies/{company_id}/access-requests",
        json={"requested_role": "accountant"},
        headers={"Authorization": "Bearer test"},
    )

    assert request_response.status_code == 200
    assert request_response.json()["status"] == "pending"

    owner = client_for(override_user(OWNER_ID, "owner@abhay.test"), monkeypatch)
    approval_response = owner.patch(
        f"/companies/{company_id}/access-requests/{FakeAccountingRepository.request_id}",
        json={"decision": "approve", "role": "accountant"},
        headers={"Authorization": "Bearer test"},
    )

    assert approval_response.status_code == 200
    assert approval_response.json()["status"] == "approved"
    accounting_routes.repo_for_company(company_id, override_user(REQUESTER_ID, "requester@abhay.test"), object())


def test_rejected_access_does_not_grant_company_access(monkeypatch) -> None:
    company_id = FakeAccountingRepository.company_id
    owner = client_for(override_user(OWNER_ID, "owner@abhay.test"), monkeypatch)

    response = owner.patch(
        f"/companies/{company_id}/access-requests/{FakeAccountingRepository.request_id}",
        json={"decision": "reject", "role": "viewer"},
        headers={"Authorization": "Bearer test"},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "rejected"

    try:
        accounting_routes.repo_for_company(company_id, override_user(REQUESTER_ID, "requester@abhay.test"), object())
    except HTTPException as exc:
        assert exc.status_code == 403
    else:
        raise AssertionError("Rejected user should not access company data.")


def test_non_owner_cannot_approve_access_request(monkeypatch) -> None:
    company_id = FakeAccountingRepository.company_id
    requester = client_for(override_user(REQUESTER_ID, "requester@abhay.test"), monkeypatch)

    response = requester.patch(
        f"/companies/{company_id}/access-requests/{FakeAccountingRepository.request_id}",
        json={"decision": "approve", "role": "viewer"},
        headers={"Authorization": "Bearer test"},
    )

    assert response.status_code == 403


def test_audit_events_endpoint_returns_recent_activity(monkeypatch) -> None:
    company_id = FakeAccountingRepository.company_id
    client = client_for(override_user(OWNER_ID, "owner@abhay.test"), monkeypatch)

    response = client.get(
        f"/companies/{company_id}/audit-events",
        headers={"Authorization": "Bearer test"},
    )

    assert response.status_code == 200
    rows = response.json()
    assert rows[0]["action_type"] == "voucher.posted"
    assert rows[0]["entity_type"] == "voucher"
    assert rows[0]["summary"] == "PAYMENT-000001"
