package com.raftly.rpc;

import com.raftly.RaftNode;
import com.raftly.LogEntry;
import java.util.concurrent.CompletableFuture;

public class RaftGrpcService implements RaftRpcService {
    private final RaftNode node;

    public RaftGrpcService(RaftNode node) {
        this.node = node;
    }

    @Override
    public CompletableFuture<VoteResponse> requestVote(VoteRequest request) {
        CompletableFuture<VoteResponse> future = new CompletableFuture<>();
        
        // Process vote request according to Raft rules
        boolean voteGranted = false;
        if (request.term() >= node.getCurrentTerm()) {
            // Check if we haven't voted yet or already voted for this candidate
            if (node.getVotedFor() == -1 || node.getVotedFor() == request.candidateId()) {
                // Check if candidate's log is at least as up-to-date as ours
                if (isLogUpToDate(request.lastLogIndex(), request.lastLogTerm())) {
                    voteGranted = true;
                    node.setVotedFor(request.candidateId());
                    node.setCurrentTerm(request.term());
                }
            }
        }

        future.complete(new VoteResponse(node.getCurrentTerm(), voteGranted));
        return future;
    }

    @Override
    public CompletableFuture<AppendEntriesResponse> appendEntries(AppendEntriesRequest request) {
        CompletableFuture<AppendEntriesResponse> future = new CompletableFuture<>();
        
        // Process append entries according to Raft rules
        boolean success = false;
        if (request.term() >= node.getCurrentTerm()) {
            node.becomeFollower();
            node.setCurrentTerm(request.term());
            
            // Check if we have the previous log entry
            if (node.getLog().hasEntry(request.prevLogIndex(), request.prevLogTerm())) {
                success = true;
                node.getLog().appendEntries(request.entries(), request.prevLogIndex());
                
                // Update commit index
                if (request.leaderCommit() > node.getCommitIndex()) {
                    node.setCommitIndex(Math.min(request.leaderCommit(), 
                        node.getLog().getLastLogIndex()));
                }
            }
        }

        future.complete(new AppendEntriesResponse(node.getCurrentTerm(), success));
        return future;
    }

    private boolean isLogUpToDate(int lastLogIndex, int lastLogTerm) {
        int ownLastIndex = node.getLog().getLastLogIndex();
        int ownLastTerm = node.getLog().getLastLogTerm();

        if (lastLogTerm > ownLastTerm) return true;
        if (lastLogTerm == ownLastTerm && lastLogIndex >= ownLastIndex) return true;
        return false;
    }
}
