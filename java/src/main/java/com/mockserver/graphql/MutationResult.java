package com.mockserver.graphql;

/**
 * Immutable result POJO for GraphQL Mutation operations.
 * Matches the GraphQL schema type {@code MutationResult}.
 */
public class MutationResult {

    private final boolean          success;
    private final String           operation;
    private final String           affectedId;
    private final MockGraphQLResponse response;

    public MutationResult(boolean success, String operation,
                          String affectedId, MockGraphQLResponse response) {
        this.success    = success;
        this.operation  = operation;
        this.affectedId = affectedId;
        this.response   = response;
    }

    public boolean           isSuccess()    { return success;    }
    public String            getOperation() { return operation;  }
    public String            getAffectedId(){ return affectedId; }
    public MockGraphQLResponse getResponse(){ return response;   }
}
