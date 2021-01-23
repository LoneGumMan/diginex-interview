Matching-engine is implemented in two parts.

# Order Book
A simple library implementing an order book with bid/ask queues. 

Order book must have a well-defined temporal ordering behavior, and as such,
the order book is deliberately made single-threaded, not thread safe.

Multi-threaded access into the order book is handled by the matching-engine.

# Matching Engine
Interfaces the order-book to the outside world through a simple 
multiple-producers-single-consumer queue so to maintain a single-threaded
access into the order book. 

Interaction with the matching engine follows a FIX-y protocol, capable of basic 
new / amend / cancel semantics.

Examples of how the matching-engine works can be found in the unit tests for 
the matching-engine.