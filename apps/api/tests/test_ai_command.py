from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_ai_command_invoice_workflow_response() -> None:
    response = client.post("/ai/command", json={"command": "Analyze this invoice bill"})

    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert "invoice" in data["summary"].lower()
    assert data["confidence"] >= 0.8
    assert len(data["actions"]) == 3


def test_ai_command_gst_workflow_response() -> None:
    response = client.post("/ai/command", json={"command": "Check GST risk"})

    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert "gst" in data["summary"].lower()


def test_ai_command_ledger_workflow_response() -> None:
    response = client.post("/ai/command", json={"command": "Map this ledger"})

    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert "ledger" in data["summary"].lower()


def test_ai_command_general_workflow_response() -> None:
    response = client.post("/ai/command", json={"command": "What should I do next?"})

    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert data["confidence"] >= 0.7


def test_ai_command_calculates_gst_from_quantity_and_rate() -> None:
    response = client.post(
        "/ai/command",
        json={"command": "30 people × 400 recharge with GST 9%"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert data["base_amount"] == 12000
    assert data["gst_rate"] == 9
    assert data["gst_amount"] == 1080
    assert data["total"] == 13080
    assert data["confidence"] >= 0.9


def test_ai_command_asks_for_gst_rate_when_missing() -> None:
    response = client.post(
        "/ai/command",
        json={"command": "400 recharge smartphone back office 30 people"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert data["base_amount"] == 12000
    assert data["gst_rate"] is None
    assert data["gst_amount"] is None
    assert data["total"] is None
    assert "which gst rate applies" in " ".join(data["actions"]).lower()


def test_ai_command_supports_arithmetic_expression() -> None:
    response = client.post("/ai/command", json={"command": "Calculate 10000 + 2500 - 500 with GST 18%"})

    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert data["base_amount"] == 12000
    assert data["gst_rate"] == 18
    assert data["gst_amount"] == 2160
    assert data["total"] == 14160


def test_routes_debug_endpoint_lists_ai_command() -> None:
    response = client.get("/routes")

    assert response.status_code == 200
    paths = {route["path"] for route in response.json()}
    assert "/ai/command" in paths
