"""
GraphQL schema and resolvers for Query and Mutation operations.

Endpoints:
  HTTP POST  /graphql   ← Queries & Mutations
  GET        /graphiql  ← In-browser IDE

Equivalent of MockGraphQLController.java + schema.graphqls.

Example Query:
  query {
    mock(resource: "orders", size: 5, format: "json") {
      requestId sizeKb byteLength timestamp payload
    }
  }

Example Mutation:
  mutation {
    create(resource: "users", size: 2, format: "json") {
      success operation affectedId
      response { sizeKb byteLength timestamp }
    }
  }
"""
import uuid
from datetime import datetime, timezone

import graphene

from app.service.payload_generator import generate, VALID_SIZES_KB


# ── Response types ────────────────────────────────────────────────────────────

class MockResponseType(graphene.ObjectType):
    """A mock API response of a specific size and format."""
    request_id = graphene.String(required=True)
    method = graphene.String(required=True)
    resource = graphene.String(required=True)
    size_kb = graphene.Int(required=True)
    format = graphene.String(required=True)
    byte_length = graphene.Int(required=True)
    timestamp = graphene.String(required=True)
    payload = graphene.String(required=True)


class HealthResponseType(graphene.ObjectType):
    """Server health and version info."""
    status = graphene.String(required=True)
    server = graphene.String(required=True)
    timestamp = graphene.String(required=True)


class ServerInfoType(graphene.ObjectType):
    """Catalogue of all available options and endpoint URLs."""
    server = graphene.String(required=True)
    valid_sizes = graphene.List(graphene.NonNull(graphene.Int), required=True)
    formats = graphene.List(graphene.NonNull(graphene.String), required=True)
    methods = graphene.List(graphene.NonNull(graphene.String), required=True)
    http_endpoint = graphene.String(required=True)
    graphiql_url = graphene.String(required=True)
    ws_subscription_url = graphene.String(required=True)
    wss_subscription_url = graphene.String(required=True)


class MutationResultType(graphene.ObjectType):
    """Result of a mutation (create / update / delete)."""
    success = graphene.Boolean(required=True)
    operation = graphene.String(required=True)
    affected_id = graphene.String()
    response = graphene.Field(MockResponseType, required=True)


# ── Helper ────────────────────────────────────────────────────────────────────

def _make_mock_response(method: str, resource: str, size_kb: int, fmt: str) -> dict:
    payload = generate(size_kb, fmt, method, resource)
    return MockResponseType(
        request_id=str(uuid.uuid4()),
        method=method.upper(),
        resource=resource,
        size_kb=size_kb,
        format=fmt.lower(),
        byte_length=len(payload.encode("utf-8")),
        timestamp=datetime.now(timezone.utc).isoformat(),
        payload=payload,
    )


# ── Queries ───────────────────────────────────────────────────────────────────

class Query(graphene.ObjectType):
    mock = graphene.Field(
        MockResponseType,
        resource=graphene.String(required=True),
        size=graphene.Int(default_value=1),
        format=graphene.String(default_value="json"),
        method=graphene.String(default_value="GET"),
        required=True,
    )
    health = graphene.Field(HealthResponseType, required=True)
    info = graphene.Field(ServerInfoType, required=True)

    def resolve_mock(self, info, resource, size=1, format="json", method="GET"):
        return _make_mock_response(method, resource, size, format)

    def resolve_health(self, info):
        return HealthResponseType(
            status="UP",
            server="MockAPIServer/1.0",
            timestamp=datetime.now(timezone.utc).isoformat(),
        )

    def resolve_info(self, info):
        return ServerInfoType(
            server="MockAPIServer/1.0",
            valid_sizes=VALID_SIZES_KB,
            formats=["json", "xml", "text", "html"],
            methods=["GET", "POST", "PUT", "DELETE"],
            http_endpoint="http://localhost:8080/graphql",
            graphiql_url="http://localhost:8080/graphiql",
            ws_subscription_url="ws://localhost:8080/graphql-ws",
            wss_subscription_url="wss://localhost:8443/graphql-ws",
        )


# ── Mutations ─────────────────────────────────────────────────────────────────

class CreateMutation(graphene.Mutation):
    """Simulate creating a resource (POST)."""

    class Arguments:
        resource = graphene.String(required=True)
        size = graphene.Int(default_value=1)
        format = graphene.String(default_value="json")

    Output = MutationResultType

    def mutate(self, info, resource, size=1, format="json"):
        response = _make_mock_response("POST", resource, size, format)
        return MutationResultType(
            success=True,
            operation="CREATE",
            affected_id=str(uuid.uuid4()),
            response=response,
        )


class UpdateMutation(graphene.Mutation):
    """Simulate updating a resource (PUT)."""

    class Arguments:
        resource = graphene.String(required=True)
        id = graphene.String(required=True)
        size = graphene.Int(default_value=1)
        format = graphene.String(default_value="json")

    Output = MutationResultType

    def mutate(self, info, resource, id, size=1, format="json"):
        response = _make_mock_response("PUT", f"{resource}/{id}", size, format)
        return MutationResultType(
            success=True,
            operation="UPDATE",
            affected_id=id,
            response=response,
        )


class DeleteMutation(graphene.Mutation):
    """Simulate deleting a resource (DELETE)."""

    class Arguments:
        resource = graphene.String(required=True)
        id = graphene.String(required=True)
        size = graphene.Int(default_value=1)
        format = graphene.String(default_value="json")

    Output = MutationResultType

    def mutate(self, info, resource, id, size=1, format="json"):
        response = _make_mock_response("DELETE", f"{resource}/{id}", size, format)
        return MutationResultType(
            success=True,
            operation="DELETE",
            affected_id=id,
            response=response,
        )


class Mutation(graphene.ObjectType):
    create = CreateMutation.Field()
    update = UpdateMutation.Field()
    delete = DeleteMutation.Field()


# ── Schema ────────────────────────────────────────────────────────────────────

schema = graphene.Schema(query=Query, mutation=Mutation)
