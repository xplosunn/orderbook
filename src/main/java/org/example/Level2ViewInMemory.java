package org.example;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

public final class Level2ViewInMemory implements Level2View {

    /*
     * This seemingly naive locking mechanism combined with non-thread safe data structures
     * performed better on initial benchmarks than one lock per side or concurrent data structures.
     */
    private final Object lock = new Object();

    /*
     * Only 1 instance of OrdersAtPrice should exist per price (per side).
     * That instance should have at least one Order.
     */
    private final Map<Long, OrdersAtPrice> asksById = new HashMap<>();
    private final SortedMap<BigDecimal, OrdersAtPrice> asksByPrice = new TreeMap<>();

    private final Map<Long, OrdersAtPrice> bidsById = new HashMap<>();
    private final SortedMap<BigDecimal, OrdersAtPrice> bidsByPrice = new TreeMap<>(Comparator.reverseOrder());

    private static class Order {
        final long id;
        long quantity;

        private Order(long id, long quantity) {
            this.id = id;
            this.quantity = quantity;
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity should be positive, got " + quantity);
            }
        }

        // no @Override equals because it's fine to compare the reference
    }

    private Map<Long, OrdersAtPrice> getMapById(Side side) {
        Objects.requireNonNull(side, () -> "side must not be null");
        return switch (side) {
            case ASK -> asksById;
            case BID -> bidsById;
        };
    }

    private SortedMap<BigDecimal, OrdersAtPrice> getMapByPrice(Side side) {
        return switch (side) {
            case ASK -> asksByPrice;
            case BID -> bidsByPrice;
        };
    }

    private static class OrdersAtPrice {
        final BigDecimal price;
        final Map<Long, Order> orders;

        private OrdersAtPrice(BigDecimal price) {
            this.price = Objects.requireNonNull(price, () -> "price must not be null");
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("price should be positive, got " + price);
            }
            this.orders = new HashMap<>();
        }

        // no @Override equals because it's fine to compare the reference
    }


    @Override
    public void onNewOrder(Side side, BigDecimal price, long quantity, long orderId) {
        final var mapById = getMapById(side);
        final var mapByPrice = getMapByPrice(side);

        final var order = new Order(orderId, quantity);
        var ordersAtPrice = new OrdersAtPrice(price);
        synchronized (lock) {
            {
                final var inserted = mapByPrice.putIfAbsent(price, ordersAtPrice);
                final var isNewOrdersAtPrice = inserted == null;
                if (!isNewOrdersAtPrice) {
                    ordersAtPrice = inserted;
                }
            }

            ordersAtPrice.orders.put(orderId, order);
            mapById.put(orderId, ordersAtPrice);
        }
    }

    @Override
    public void onCancelOrder(long orderId) {
        cancelOrder(orderId);
    }

    private Side cancelOrder(long orderId) {
        synchronized (lock) {
            var ordersAtPrice = asksById.get(orderId);
            var mapById = asksById;
            var mapByPrice = asksByPrice;
            var side = Side.ASK;
            if (ordersAtPrice == null) {
                ordersAtPrice = bidsById.get(orderId);
                mapById = bidsById;
                mapByPrice = bidsByPrice;
                side = Side.BID;
            }
            if (ordersAtPrice == null) {
                throw new IllegalArgumentException("didn't find an order with id " + orderId);
            }

            ordersAtPrice.orders.remove(orderId);
            if (ordersAtPrice.orders.isEmpty()) {
                mapById.remove(orderId);
                mapByPrice.remove(ordersAtPrice.price);
            }

            return side;
        }
    }

    @Override
    public void onReplaceOrder(BigDecimal price, long quantity, long orderId) {
        synchronized (lock) {
            final var side = cancelOrder(orderId);
            onNewOrder(side, price, quantity, orderId);
        }
    }

    @Override
    public void onTrade(long quantity, long restingOrderId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity should be positive, got " + quantity);
        }
        synchronized (lock) {
            var ordersAtPrice = asksById.get(restingOrderId);
            if (ordersAtPrice == null) {
                ordersAtPrice = bidsById.get(restingOrderId);
            }
            if (ordersAtPrice == null) {
                throw new IllegalArgumentException("didn't find an order with id " + restingOrderId);
            }
            final var order = ordersAtPrice.orders.get(restingOrderId);
            order.quantity = order.quantity - quantity;
            if (order.quantity == 0) {
                cancelOrder(restingOrderId);
            }
        }
    }

    @Override
    public long getSizeForPriceLevel(Side side, BigDecimal price) {
        final var mapByPrice = getMapByPrice(side);
        synchronized (lock) {
            final var ordersAtPrice = mapByPrice.get(price);
            if (ordersAtPrice == null) {
                return 0L;
            }
            return ordersAtPrice.orders.size();
        }
    }

    @Override
    public long getBookDepth(Side side) {
        final var mapByPrice = getMapByPrice(side);
        synchronized (lock) {
            return mapByPrice.size();
        }
    }

    @Override
    public BigDecimal getTopOfBook(Side side) {
        final var mapByPrice = getMapByPrice(side);
        synchronized (lock) {
            try {
                return mapByPrice.firstKey();
            } catch (NoSuchElementException e) {
                return null;
            }
        }
    }

    public record Entry(long id, long quantity, BigDecimal price) {}

    public Stream<Entry> streamOrdersForTesting() {
        return Stream.of(asksByPrice, bidsByPrice)
                .flatMap(map -> map.values().stream())
                .flatMap(ordersAtPrice ->
                        ordersAtPrice.orders.values().stream().map(order -> new Entry(order.id, order.quantity, ordersAtPrice.price))
                );
    }

}
