Another proposal change:
- Doing migration during load balanceing
```mermaid
flowchart TD
A[Initialize] --> B{Is lease store initialized?}
B -- Yes --> C[Finish]
B -- No -->  D[Acquire Lock]
D --> E{Pkversion lease store exists?}
E -- YES -->  F[Mark lease store initialized]
F --> C
E -- No --> G[Create all leases for pkranges ]
G --> F
```
```mermaid
 graph TD
A[Get all leases from epk version lease store] --> B[Get all leases from pk version lease store]
B --> C[CategorizeLeases]
C --> D[Calculate workers count from epk lease store]
D --> E[Calculate expected leases count for the current worker]
E --> F{Any expired lease?}
F -- Yes --> G[Take expired lease]
G --> H[Finish]
F -- No -->I[Stealing lease]
I --> H
```

- Customer opt-in
  - public ChangeFeedProcessorOptions setStartFromLeaseStore(CosmosAsyncContainer oldLeaseContainer, LeaseVersion)
