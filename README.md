# Raftly

A Java implementation of the Raft consensus algorithm, providing a robust distributed consensus solution.

## Overview

Raftly is a complete implementation of the Raft consensus protocol, featuring:
- Leader election with randomized timeouts
- Log replication with consistency guarantees
- State machine replication
- Membership management
- Thread-safe operations
- Configurable timeouts and heartbeat intervals
- Proper error handling and recovery mechanisms

## Getting Started

### Prerequisites
- Java 8 or higher
- Maven 3.6 or higher
- Git

### Setup
1. Clone the repository:
```bash
git clone https://github.com/sajalkmr/raftly.git
cd raftly
```

2. Build the project:
```bash
mvn clean install
```

### Running the Demo
1. Start a local cluster with 3 nodes:
```bash
mvn exec:java -Dexec.mainClass="com.raftly.RaftDemo"
```

2. The demo will:
- Initialize a 3-node Raft cluster
- Demonstrate leader election
- Show log replication in action
- Display state machine consistency

## Configuration

### Default Settings
```properties
# Core Raft settings
raft.election.timeout.base=1500
raft.election.timeout.range=750
raft.heartbeat.interval=1000
raft.commit.check.interval=500

# Cluster settings
raft.cluster.size=3
```

### Customizing Configuration
You can modify these settings by:
1. Updating the constants in `RaftNode.java`
2. Passing custom values during node initialization

## Architecture

### Core Components

#### RaftNode
- Implements the core Raft consensus logic
- Manages node state (Follower/Candidate/Leader)
- Handles leader election and log replication
- Uses scheduled tasks for timeouts and heartbeats

#### Log
- Thread-safe log entry management
- Handles log consistency and conflict resolution
- Supports atomic append operations
- Maintains log indices and terms

#### StateMachine
- Applies committed log entries
- Maintains consistent state across the cluster
- Supports state snapshots and recovery

### Thread Safety
- Uses `ReentrantLock` for state modifications
- Atomic operations for critical state changes
- Read-write locks for log access
- Thread-safe scheduled operations

## Usage Examples

### Basic Cluster Setup
```java
// Initialize components
StateMachine stateMachine = new StateMachine();
Log log = new Log();
RaftNode node = new RaftNode(0, null, stateMachine, log);

// Create and set up cluster
List<RaftNode> nodes = Arrays.asList(node);
RaftCluster cluster = new RaftCluster(nodes);
node.setCluster(cluster);

// Start the node
node.start();
```

### Command Replication
```java
// Append a command through the leader
if (node.isLeader()) {
    LogEntry entry = new LogEntry(currentTerm, "SET key value");
    CompletableFuture<Boolean> future = node.appendCommand(entry);
    boolean success = future.get(); // Wait for replication
}
```

## Error Handling

The implementation includes robust error handling for:
- Network partitions
- Node failures
- Split votes
- Log inconsistencies
- Concurrent operations

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Based on the [Raft Consensus Algorithm paper](https://raft.github.io/raft.pdf) by Diego Ongaro and John Ousterhout
