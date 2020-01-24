package org.example.rook;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RookMovement {

    private static final int DESK_SIZE = 8;
    private static final int NUMBER_OF_MOVES = 50;

    private static final int SLEEP_LOWER_BOUND = 200;
    private static final int SLEEP_UPPER_BOUND = 300;

    private static final int MOVE_TIMEOUT = 5000;

    private List<Rook> rooks;
    private List<List<Integer>> desk;

    RookMovement(int rookNumber) {
        this.desk = buildDesk();
        this.rooks = buildRooks(rookNumber);
    }

    private List<List<Integer>> buildDesk() {
        List<List<Integer>> desk = new CopyOnWriteArrayList<>();
        for (int i = 0; i < DESK_SIZE; i++) {
            List<Integer> column = new CopyOnWriteArrayList<>();
            for (int j = 0; j < DESK_SIZE; j++) {
                column.add(0);
            }
            desk.add(column);
        }
        return desk;
    }

    private List<Rook> buildRooks(int rookNumber) {
        List<Rook> rooks = new ArrayList<>(rookNumber);
        log.info("Init rook positions");
        for (int i = 1; i <= rookNumber; i++) {
            while (true) {
                int row = ThreadLocalRandom.current().nextInt(DESK_SIZE);
                int column = ThreadLocalRandom.current().nextInt(DESK_SIZE);
                if (desk.get(column).get(row) == 0) {
                    desk.get(column).set(row, i);
                    Rook rook = new Rook(i, new Position(row, column));
                    rooks.add(rook);
                    log.info("INITIAL POSITION {}: {}:{}", rook.getId(), row, column);
                    break;
                }
                log.info("Collision occurred while init rook positions: rook: {}, row: {}, col: {}", i, row, column);
            }
        }
        return rooks;
    }

    public void move() {
        ExecutorService executorService = Executors.newFixedThreadPool(rooks.size());
        AtomicInteger atomicId = new AtomicInteger(0);
        for (int i = 0; i < rooks.size(); i++) {
            executorService.submit(() -> {
                int rookId = atomicId.getAndIncrement();
                Rook rook = rooks.get(rookId);
                for (int k = 0; k < NUMBER_OF_MOVES; k++) {
                    try {
                        move(rook);
                        Thread.sleep(ThreadLocalRandom.current().nextInt(SLEEP_LOWER_BOUND, SLEEP_UPPER_BOUND));
                    } catch (InterruptedException e) {
                        log.error("Interrupted for rook {}: {}", rook, e.getMessage(), e);
                    }
                }
                log.info("Rook {} has finished moving", rook);
            });
        }
        executorService.shutdown();
    }

    private void move(Rook rook) throws InterruptedException {
        while (true) {
            Position nextPosition = getNextPosition(rook.getPosition());
            if (move(rook, nextPosition)) {
                log.info("POSITION {}: {}:{}", rook.getId(), nextPosition.getRow(), nextPosition.getColumn());
                return;
            }
        }
    }

    private Position getNextPosition(Position currentPosition) {
        boolean moveInRow = ThreadLocalRandom.current().nextInt(1000) % 2 == 0;
        if (moveInRow) {
            while (true) {
                int column = ThreadLocalRandom.current().nextInt(DESK_SIZE);
                if (column != currentPosition.getColumn()) {
                    return new Position(currentPosition.getRow(), column);
                }
            }
        } else {
            while (true) {
                int row = ThreadLocalRandom.current().nextInt(DESK_SIZE);
                if (row != currentPosition.getRow()) {
                    return new Position(row, currentPosition.getColumn());
                }
            }
        }
    }

    private boolean move(Rook rook, Position to) throws InterruptedException {
        log.info("Move {}: {} -> {}", rook.getId(), rook.getPosition(), to);
        long start = System.currentTimeMillis();
        Position from = rook.getPosition();
        List<Position> locked = new ArrayList<>();
        if (from.getRow() == to.getRow()) {
            int row = from.getRow();
            if (!moveInRow(start, locked, rook, from.getColumn(), to.getColumn(), row)) {
                return false;
            }
        } else {
            int col = from.getColumn();
            if (!moveInColumn(start, locked, rook, from.getRow(), to.getRow(), col)) {
                return false;
            }
        }
        releaseWay(from, locked, rook.getId());
        rook.setPosition(to);
        return true;
    }

    private boolean moveInRow(long start, List<Position> locked, Rook rook, int from, int to, int row) throws InterruptedException {
        if (to > from) {
            for (int col = from + 1; col <= to; col++) {
                if (!tryToLockNextPosition(start, locked, rook, new Position(row, col))) {
                    return false;
                }
            }
        } else {
            for (int col = from - 1; col >= to; col--) {
                if (!tryToLockNextPosition(start, locked, rook, new Position(row, col))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean moveInColumn(long start, List<Position> locked, Rook rook, int from, int to, int col) throws InterruptedException {
        if (to > from) {
            for (int row = from + 1; row <= to; row++) {
                if (!tryToLockNextPosition(start, locked, rook, new Position(row, col))) {
                    return false;
                }
            }
        } else {
            for (int row = from - 1; row >= to; row--) {
                if (!tryToLockNextPosition(start, locked, rook, new Position(row, col))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean tryToLockNextPosition(long start, List<Position> locked, Rook rook, Position position) throws InterruptedException {
        while (true) {
            if (System.currentTimeMillis() - start > MOVE_TIMEOUT) {
                log.info("Way for rook {} is busy. Another way will be chosen", rook);
                for (Position lock : locked) {
                    release(lock, rook.getId());
                }
                return false;
            }
            if (isBusy(position)) {
                Thread.sleep(SLEEP_LOWER_BOUND);
            } else {
                lock(position, rook.getId());
                locked.add(position);
                return true;
            }
        }
    }

    private void releaseWay(Position from, List<Position> locked, int rook) {
        release(from, rook);
        for (int i = 0; i < locked.size() - 1; i++) {
            release(new Position(locked.get(i).getRow(), locked.get(i).getColumn()), rook);
        }
    }

    private void lock(Position position, int lock) {
        log.debug("Lock {}:{} by {}", position.getRow(), position.getColumn(), lock);
        desk.get(position.getColumn()).set(position.getRow(), lock);
    }

    private void release(Position position, int lock) {
        log.debug("Release {}:{} by {}", position.getRow(), position.getColumn(), lock);
        desk.get(position.getColumn()).set(position.getRow(), 0);
    }

    private boolean isBusy(Position position) {
        return desk.get(position.getColumn()).get(position.getRow()) > 0;
    }
}
