package com.raftly.rpc;

import com.raftly.LogEntry;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RaftRpcService {
    CompletableFuture<VoteResponse> requestVote(VoteRequest request);
    CompletableFuture<AppendEntriesResponse> appendEntries(AppendEntriesRequest request);
}

record VoteRequest(
    int term,
    int candidateId,
    int lastLogIndex,
    int lastLogTerm
) {}

record VoteResponse(
    int term,
    boolean voteGranted
) {}

record AppendEntriesRequest(
    int term,
    int leaderId,
    int prevLogIndex,
    int prevLogTerm,
    List<LogEntry> entries,
    int leaderCommit
) {}

record AppendEntriesResponse(
    int term,
    boolean success
) {}
