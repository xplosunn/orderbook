package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.example.Level2View.Side;
import static org.example.Level2ViewInMemory.Entry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Level2ViewTests {

    private static Level2ViewInMemory underTest() {
        return new Level2ViewInMemory();
    }

    @Nested
    class OnNewOrder {

        @Test
        void failures() {
            final var view = underTest();
            {
                var exception = assertThrows(
                        NullPointerException.class,
                        () -> view.onNewOrder(null, BigDecimal.ONE, 1L, 1L)
                );
                assertEquals("side must not be null", exception.getMessage());
            }
            {
                var exception = assertThrows(
                        NullPointerException.class,
                        () -> view.onNewOrder(Side.BID, null, 1L, 1L)
                );
                assertEquals("price must not be null", exception.getMessage());
            }
            {
                var exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> view.onNewOrder(Side.BID, BigDecimal.valueOf(-1L), 1L, 1L)
                );
                assertEquals("price should be positive, got -1", exception.getMessage());
            }
            {
                var exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> view.onNewOrder(Side.BID, BigDecimal.ZERO, 1L, 1L)
                );
                assertEquals("price should be positive, got 0", exception.getMessage());
            }
            {
                var exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> view.onNewOrder(Side.BID, BigDecimal.ONE, -1L, 1L)
                );
                assertEquals("quantity should be positive, got -1", exception.getMessage());
            }
            {
                var exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> view.onNewOrder(Side.BID, BigDecimal.ONE, 0L, 1L)
                );
                assertEquals("quantity should be positive, got 0", exception.getMessage());
            }
        }
    }

    @Nested
    class OnCancelOrder {
        @Test
        void nonExisting() {
            final var view = underTest();
            final var orderId = 1L;
            final var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> view.onCancelOrder(orderId)
            );
            assertEquals("didn't find an order with id 1", exception.getMessage());
        }

        @Test
        void cancelAfterFullyTraded() {
            final var view = underTest();
            final var orderId = 1L;
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 2L, orderId);
            view.onTrade(2L, orderId);

            var entries = view.streamOrdersForTesting().collect(Collectors.toSet());
            assertEquals(
                    Set.of(),
                    entries
            );

            final var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> view.onCancelOrder(orderId)
            );
            assertEquals("didn't find an order with id 1", exception.getMessage());
        }

        @Test
        void cancelTwice() {
            final var view = underTest();
            final var orderId = 1L;
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 1L, orderId);
            view.onCancelOrder(orderId);
            final var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> view.onCancelOrder(orderId)
            );
            assertEquals("didn't find an order with id 1", exception.getMessage());
        }

        @Test
        void successBeforeTrade() {
            final var view = underTest();
            final var orderId = 1L;
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 1L, orderId);
            view.onCancelOrder(orderId);
        }

        @Test
        void successAfterTrade() {
            final var view = underTest();
            final var orderId = 1L;
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 2L, orderId);
            view.onTrade(1L, orderId);
            view.onCancelOrder(orderId);
        }
    }

    @Nested
    class OnReplaceOrder {
        @Test
        void nonExisting() {
            final var view = underTest();
            final var orderId = 1L;
            final var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> view.onReplaceOrder(BigDecimal.TEN, 10L, orderId)
            );
            assertEquals("didn't find an order with id 1", exception.getMessage());
        }

        @Test
        void success() {
            final var view = underTest();
            final var orderId = 1L;
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 2L, orderId);
            view.onReplaceOrder(BigDecimal.TEN, 3L, orderId);

            view.onNewOrder(Side.ASK, BigDecimal.ONE, 5L, 2L);

            final var entries = view.streamOrdersForTesting().collect(Collectors.toSet());
            assertEquals(
                    Set.of(
                            new Entry(1L, 3L, BigDecimal.TEN),
                            new Entry(2L, 5L, BigDecimal.ONE)
                    ),
                    entries
            );
        }
    }

    @Nested
    class OnTrade {
        @Test
        void nonExisting() {
            final var view = underTest();
            final var orderId = 1L;
            final var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> view.onTrade(1L, orderId)
            );
            assertEquals("didn't find an order with id 1", exception.getMessage());
        }

        @Test
        void success() {
            final var view = underTest();
            final var orderId = 1L;
            view.onNewOrder(Side.ASK, BigDecimal.TEN, 3L, orderId);

            view.onTrade(1L, orderId);

            var entries = view.streamOrdersForTesting().collect(Collectors.toSet());
            assertEquals(
                    Set.of(
                            new Entry(1L, 2L, BigDecimal.TEN)
                    ),
                    entries
            );

            view.onTrade(2L, orderId);

            entries = view.streamOrdersForTesting().collect(Collectors.toSet());
            assertEquals(
                    Set.of(),
                    entries
            );
        }
    }

    @Nested
    class GetSizeForPriceLevel {
        @Test
        void zero() {
            final var view = underTest();
            assertEquals(0L, view.getSizeForPriceLevel(Side.ASK, BigDecimal.ONE));
        }

        @Test
        void success() {
            final var view = underTest();

            view.onNewOrder(Side.ASK, BigDecimal.TEN, 3L, 1L);
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 5L, 2L);
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 7L, 3L);

            assertEquals(1L, view.getSizeForPriceLevel(Side.ASK, BigDecimal.TEN));
            assertEquals(2L, view.getSizeForPriceLevel(Side.ASK, BigDecimal.ONE));
        }
    }

    @Nested
    class GetBookDepth {
        @Test
        void zero() {
            final var view = underTest();
            assertEquals(0L, view.getBookDepth(Side.ASK));
        }

        @Test
        void success() {
            final var view = underTest();

            view.onNewOrder(Side.ASK, BigDecimal.TEN, 3L, 1L);
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 5L, 2L);
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 7L, 3L);

            assertEquals(2L, view.getBookDepth(Side.ASK));
        }
    }


    @Nested
    class GetTopOfBook {
        @Test
        void nullCase() {
            final var view = underTest();
            assertEquals(null, view.getTopOfBook(Side.ASK));
        }

        @Test
        void highestBid() {
            final var view = underTest();

            view.onNewOrder(Side.BID, BigDecimal.TEN, 3L, 1L);
            view.onNewOrder(Side.BID, BigDecimal.TEN, 4L, 2L);
            view.onNewOrder(Side.BID, BigDecimal.ONE, 5L, 3L);

            assertEquals(BigDecimal.TEN, view.getTopOfBook(Side.BID));
            view.onCancelOrder(1L);
            assertEquals(BigDecimal.TEN, view.getTopOfBook(Side.BID));
            view.onCancelOrder(2L);
            assertEquals(BigDecimal.ONE, view.getTopOfBook(Side.BID));
        }

        @Test
        void lowestAsk() {
            final var view = underTest();

            view.onNewOrder(Side.ASK, BigDecimal.TEN, 3L, 1L);
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 3L, 2L);
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 5L, 3L);

            assertEquals(BigDecimal.ONE, view.getTopOfBook(Side.ASK));
            view.onCancelOrder(3L);
            assertEquals(BigDecimal.ONE, view.getTopOfBook(Side.ASK));
            view.onCancelOrder(2L);
            assertEquals(BigDecimal.TEN, view.getTopOfBook(Side.ASK));
        }
    }

    @Nested
    class ConcurrentScenarios {

        private static final int CONCURRENCY_RANGE = 100_000;

        @Test
        void onNewOrder() {
            final var view = underTest();

            IntStream.range(1, CONCURRENCY_RANGE).parallel()
                    .forEach(id -> view.onNewOrder(Side.ASK, BigDecimal.valueOf((id % 2) + 1), id * 2, id));

            assertEquals(
                    IntStream.range(1, CONCURRENCY_RANGE)
                            .mapToObj(id -> new Entry(id, id * 2, BigDecimal.valueOf((id % 2) + 1)))
                            .collect(Collectors.toSet()),
                    view.streamOrdersForTesting().collect(Collectors.toSet())
            );
        }

        @Test
        void onCancelOrder() {
            final var view = underTest();

            IntStream.range(1, CONCURRENCY_RANGE).parallel()
                    .mapToObj(id -> new Entry(id, id * 2, BigDecimal.valueOf((id % 2) + 1)))
                    .peek(entry -> view.onNewOrder(Side.ASK, entry.price(), entry.quantity(), entry.id()))
                    .filter(entry -> entry.id() % 2 == 0)
                    .forEach(entry -> view.onCancelOrder(entry.id()));

            assertEquals(
                    IntStream.range(1, CONCURRENCY_RANGE)
                            .mapToObj(id -> new Entry(id, id * 2, BigDecimal.valueOf((id % 2) + 1)))
                            .filter(entry -> entry.id() % 2 != 0)
                            .collect(Collectors.toSet()),
                    view.streamOrdersForTesting().collect(Collectors.toSet())
            );
        }

        @Test
        void onReplaceOrder() {
            final var view = underTest();

            IntStream.range(1, CONCURRENCY_RANGE).parallel()
                    .mapToObj(id -> new Entry(id, id * 2, BigDecimal.valueOf((id % 2) + 2)))
                    .peek(entry -> view.onNewOrder(Side.ASK, entry.price(), entry.quantity(), entry.id()))
                    .forEach(entry -> view.onReplaceOrder(entry.price().subtract(BigDecimal.ONE), entry.quantity(), entry.id()));

            assertEquals(
                    IntStream.range(1, CONCURRENCY_RANGE)
                            .mapToObj(id -> new Entry(id, id * 2, BigDecimal.valueOf((id % 2) + 1)))
                            .collect(Collectors.toSet()),
                    view.streamOrdersForTesting().collect(Collectors.toSet())
            );
        }

        @Test
        void onReplaceOrderIsAtomic() {
            final var view = underTest();

            view.onNewOrder(Side.ASK, BigDecimal.TEN, 1, 1L);
            view.onNewOrder(Side.ASK, BigDecimal.ONE, 1, 2L);

            IntStream.range(1, CONCURRENCY_RANGE).parallel()
                    .forEach(__ -> {
                        view.onReplaceOrder(BigDecimal.ONE, 1, 2L);
                        assertEquals(
                                BigDecimal.ONE,
                                view.getTopOfBook(Side.ASK)
                        );
                        assertEquals(
                                1,
                                view.getSizeForPriceLevel(Side.ASK, BigDecimal.ONE)
                        );
                        assertEquals(
                                2L,
                                view.getBookDepth(Side.ASK)
                        );
                    });
        }

        @Test
        void onTrade() {
            final var view = underTest();

            IntStream.range(1, CONCURRENCY_RANGE).parallel()
                    .mapToObj(id -> new Entry(id, id * 2, BigDecimal.valueOf((id % 2) + 1)))
                    .peek(entry -> view.onNewOrder(Side.ASK, entry.price(), entry.quantity(), entry.id()))
                    .forEach(entry -> {
                        if (entry.id() % 2 == 0) {
                            view.onTrade(entry.quantity(), entry.id());
                        } else {
                            view.onTrade(1L, entry.id());
                        }
                    });

            assertEquals(
                    IntStream.range(1, CONCURRENCY_RANGE)
                            .mapToObj(id -> new Entry(id, id * 2 - 1, BigDecimal.valueOf((id % 2) + 1)))
                            .filter(entry -> entry.id() % 2 != 0)
                            .collect(Collectors.toSet()),
                    view.streamOrdersForTesting().collect(Collectors.toSet())
            );
        }

        @Test
        void onTradeSameOrder() {
            final var view = underTest();

            view.onNewOrder(Side.ASK, BigDecimal.TEN, CONCURRENCY_RANGE, 1L);

            IntStream.range(1, CONCURRENCY_RANGE).parallel()
                    .forEach(__ -> view.onTrade(1L, 1L));

            assertEquals(
                    Set.of(
                            new Entry(1L, 1L, BigDecimal.TEN)
                    ),
                    view.streamOrdersForTesting().collect(Collectors.toSet())
            );
        }

        @Test
        void randomOps() {
            final var view = underTest();

            final Predicate<Entry> randomOpFilter = (entry) -> {
                switch (new Random().nextInt(6)) {
                    case 0:
                        view.onCancelOrder(entry.id());
                        return false;
                    case 1:
                        view.onReplaceOrder(entry.price(), entry.quantity(), entry.id());
                        return true;
                    case 2:
                        view.onTrade(entry.quantity(), entry.id());
                        return false;
                    case 3:
                        view.getSizeForPriceLevel(Side.ASK, entry.price());
                        return true;
                    case 4:
                        view.getBookDepth(Side.ASK);
                        return true;
                    default:
                        view.getTopOfBook(Side.ASK);
                        return true;
                }
            };

            final var expected = IntStream.range(1, CONCURRENCY_RANGE).parallel()
                    .mapToObj(id -> new Entry(id, id * 2, BigDecimal.valueOf((id % 2) + 1)))
                    .peek(entry -> view.onNewOrder(Side.ASK, entry.price(), entry.quantity(), entry.id()))
                    .filter(randomOpFilter)
                    .filter(randomOpFilter)
                    .filter(randomOpFilter)
                    .collect(Collectors.toSet());

            assertEquals(
                    expected,
                    view.streamOrdersForTesting().collect(Collectors.toSet())
            );
        }
    }
}
