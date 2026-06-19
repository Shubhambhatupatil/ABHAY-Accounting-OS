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


def test_routes_debug_endpoint_lists_ai_command() -> None:
    response = client.get("/routes")

    assert response.status_code == 200
    paths = {route["path"] for route in response.json()}
    assert "/ai/command" in paths
