/*
 * Copyright © 2016 spypunk <spypunk@gmail.com>
 *
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See the COPYING file for more details.
 */

package spypunk.tetris.service;

import static spypunk.tetris.constants.TetrisConstants.HEIGHT;
import static spypunk.tetris.constants.TetrisConstants.WIDTH;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import spypunk.tetris.factory.ShapeFactory;
import spypunk.tetris.model.Block;
import spypunk.tetris.model.Movement;
import spypunk.tetris.model.Shape;
import spypunk.tetris.model.ShapeType;
import spypunk.tetris.model.Tetris;
import spypunk.tetris.model.Tetris.State;
import spypunk.tetris.model.TetrisEvent;
import spypunk.tetris.model.TetrisInstance;

@Singleton
public class TetrisServiceImpl implements TetrisService {

    private static final int ROWS_PER_LEVEL = 10;

    private final ShapeFactory shapeFactory;

    private final Map<Integer, Integer> scorePerRows = ImmutableMap.of(1, 40, 2, 100, 3, 300, 4, 1200);

    private final Map<Integer, Integer> levelSpeeds = createLevelSpeeds();

    @Inject
    public TetrisServiceImpl(final ShapeFactory shapeFactory) {
        this.shapeFactory = shapeFactory;
    }

    @Override
    public void start(final Tetris tetris) {
        final Map<ShapeType, Integer> statistics = Lists.newArrayList(ShapeType.values()).stream()
                .collect(Collectors.toMap(shapeType -> shapeType, shapeType -> 0));

        final int speed = getLevelSpeed(0);

        final TetrisInstance tetrisInstance = TetrisInstance.Builder.instance()
                .setStatistics(statistics).setSpeed(speed).build();

        tetrisInstance.setNextShape(shapeFactory.createRandomShape());

        getNextShape(tetrisInstance);

        tetris.setTetrisInstance(tetrisInstance);

        tetris.setState(State.RUNNING);
    }

    @Override
    public void update(final Tetris tetris) {
        if (!isTetrisRunning(tetris)) {
            return;
        }

        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        tetrisInstance.setCurrentGravityFrame(tetrisInstance.getCurrentGravityFrame() + 1);

        if (handleNextShape(tetris)) {
            if (tetrisInstance.isHardDropEnabled()) {
                handleHardDrop(tetris);
            } else if (handleMovement(tetris)) {
                handleGravity(tetris);
            }
        }
    }

    @Override
    public void pause(final Tetris tetris) {
        tetris.setState(tetris.getState().onPause());
    }

    @Override
    public void triggerMovement(final Tetris tetris, final Movement movement) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        if (isTetrisRunning(tetris) && !tetrisInstance.isCurrentShapeLocked()
                && !tetrisInstance.isHardDropEnabled()) {
            tetrisInstance.setMovement(Optional.of(movement));
        }
    }

    @Override
    public void triggerHardDrop(final Tetris tetris) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        if (isTetrisRunning(tetris) && !tetrisInstance.isCurrentShapeLocked()
                && !tetrisInstance.isHardDropEnabled()) {
            tetrisInstance.setHardDropEnabled(true);
        }
    }

    @Override
    public void mute(final Tetris tetris, final boolean mute) {
        tetris.setMute(mute);
    }

    private void handleHardDrop(final Tetris tetris) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        moveShapeDown(tetris);
        updateScoreWithCompletedMovement(tetrisInstance);
    }

    private boolean handleNextShape(final Tetris tetris) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        if (!tetrisInstance.isCurrentShapeLocked()) {
            return true;
        }

        if (isTimeToHandleGravity(tetrisInstance)) {
            clearCompleteRows(tetris);
            getNextShape(tetrisInstance);
            checkShapeIsLocked(tetris);

            if (!tetrisInstance.isCurrentShapeLocked()) {
                resetCurrentGravityFrame(tetrisInstance);
            }
        }

        return false;
    }

    private boolean handleMovement(final Tetris tetris) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        final Optional<Movement> optionalMovement = tetrisInstance.getMovement();

        if (!optionalMovement.isPresent()) {
            return true;
        }

        final Movement movement = optionalMovement.get();

        tetrisInstance.setMovement(Optional.empty());

        final boolean isDownMovement = Movement.DOWN.equals(movement);

        if (isDownMovement || canShapeMove(tetrisInstance, movement)) {
            moveShape(tetris, movement);

            if (isDownMovement) {
                updateScoreWithCompletedMovement(tetrisInstance);
            }

            return !tetrisInstance.isCurrentShapeLocked();
        }

        return true;
    }

    private void handleGravity(final Tetris tetris) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        if (!isTimeToHandleGravity(tetrisInstance)) {
            return;
        }

        moveShapeDown(tetris);

        resetCurrentGravityFrame(tetrisInstance);
    }

    private void checkShapeIsLocked(final Tetris tetris) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        if (canShapeMove(tetrisInstance, Movement.DOWN)) {
            return;
        }

        tetrisInstance.getCurrentShape().getBlocks()
                .forEach(block -> tetrisInstance.getBlocks().put(block.getLocation(), block));

        if (isGameOver(tetrisInstance)) {
            tetris.setState(State.GAME_OVER);
            tetris.getTetrisEvents().add(TetrisEvent.GAME_OVER);
        } else {
            resetCurrentGravityFrame(tetrisInstance);
            tetris.getTetrisEvents().add(TetrisEvent.SHAPE_LOCKED);
            tetrisInstance.setCurrentShapeLocked(true);
        }

        tetrisInstance.setHardDropEnabled(false);
    }

    private void getNextShape(final TetrisInstance tetrisInstance) {
        final Shape currentShape = tetrisInstance.getNextShape();

        tetrisInstance.setCurrentShape(currentShape);
        tetrisInstance.setCurrentShapeLocked(false);
        tetrisInstance.setNextShape(shapeFactory.createRandomShape());

        updateStatistics(tetrisInstance);
    }

    private void updateStatistics(final TetrisInstance tetrisInstance) {
        final ShapeType shapeType = tetrisInstance.getCurrentShape().getShapeType();
        final Map<ShapeType, Integer> statistics = tetrisInstance.getStatistics();
        final Integer count = statistics.get(shapeType);

        statistics.put(shapeType, count + 1);
    }

    private boolean isGameOver(final TetrisInstance tetrisInstance) {
        return tetrisInstance.getBlocks().values().stream()
                .anyMatch(block -> block.getLocation().y == 0);
    }

    private boolean isTimeToHandleGravity(final TetrisInstance tetrisInstance) {
        return tetrisInstance.getCurrentGravityFrame() > tetrisInstance.getSpeed();
    }

    private void clearCompleteRows(final Tetris tetris) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        final List<Integer> completeRows = IntStream.range(0, HEIGHT)
                .filter(row -> isRowComplete(tetrisInstance, row)).boxed().collect(Collectors.toList());

        final int completedRows = completeRows.size();

        if (completedRows == 0) {
            return;
        }

        completeRows.forEach(row -> clearCompleteRow(tetrisInstance, row));

        tetrisInstance.setCompletedRows(tetrisInstance.getCompletedRows() + completedRows);

        updateScoreWithCompletedRows(tetrisInstance, completedRows);
        updateLevel(tetrisInstance);

        tetris.getTetrisEvents().add(TetrisEvent.ROWS_COMPLETED);
    }

    private void updateLevel(final TetrisInstance tetrisInstance) {
        final int completedRows = tetrisInstance.getCompletedRows();
        final int nextLevel = tetrisInstance.getLevel() + 1;

        if (completedRows >= ROWS_PER_LEVEL * nextLevel) {
            tetrisInstance.setLevel(nextLevel);

            final int speed = getLevelSpeed(nextLevel);

            tetrisInstance.setSpeed(speed);
        }
    }

    private void updateScoreWithCompletedRows(final TetrisInstance tetrisInstance, final int completedRows) {
        final Integer rowsScore = scorePerRows.get(completedRows);
        final int score = tetrisInstance.getScore();

        tetrisInstance.setScore(score + rowsScore * (tetrisInstance.getLevel() + 1));
    }

    private void updateScoreWithCompletedMovement(final TetrisInstance tetrisInstance) {
        tetrisInstance.setScore(tetrisInstance.getScore() + 1);
    }

    private void clearCompleteRow(final TetrisInstance tetrisInstance, final Integer row) {
        final Map<Point, Block> blocks = tetrisInstance.getBlocks();

        final List<Block> blocksToMoveDown = blocks.values().stream()
                .filter(block -> block.getLocation().y < row)
                .collect(Collectors.toList());

        IntStream.range(0, WIDTH)
                .forEach(column -> clearBlockAt(tetrisInstance, new Point(column, row)));

        blocksToMoveDown.forEach(block -> clearBlockAt(tetrisInstance, block.getLocation()));
        blocksToMoveDown.forEach(block -> moveBlockDown(tetrisInstance, block));
    }

    private void clearBlockAt(final TetrisInstance tetrisInstance, final Point location) {
        tetrisInstance.getBlocks().remove(location);
    }

    private boolean isRowComplete(final TetrisInstance tetrisInstance, final int row) {
        return IntStream.range(0, WIDTH)
                .allMatch(column -> tetrisInstance.getBlocks().containsKey(new Point(column, row)));
    }

    private void moveShape(final Tetris tetris, final Movement movement) {
        final TetrisInstance tetrisInstance = tetris.getTetrisInstance();

        final Shape currentShape = tetrisInstance.getCurrentShape();
        final Shape newShape = movement.apply(currentShape);

        tetrisInstance.setCurrentShape(newShape);

        checkShapeIsLocked(tetris);
    }

    private void moveBlockDown(final TetrisInstance tetrisInstance, final Block block) {
        final Point location = block.getLocation();
        final Point newLocation = Movement.DOWN.apply(location);

        block.setLocation(newLocation);

        tetrisInstance.getBlocks().put(block.getLocation(), block);
    }

    private boolean canShapeMove(final TetrisInstance tetrisInstance, final Movement movement) {
        final Shape currentShape = tetrisInstance.getCurrentShape();
        final Shape newShape = movement.apply(currentShape);

        return newShape.getBlocks().stream().allMatch(block -> canBlockMove(tetrisInstance, block));
    }

    private boolean canBlockMove(final TetrisInstance tetrisInstance, final Block block) {
        final Point location = block.getLocation();

        if (location.x < 0 || location.x == WIDTH || location.y < 0 || location.y == HEIGHT) {
            return false;
        }

        final Block nextLocationBlock = tetrisInstance.getBlocks().get(location);

        return nextLocationBlock == null;
    }

    private boolean isTetrisRunning(final Tetris tetris) {
        return tetris.getState().equals(State.RUNNING);
    }

    private void resetCurrentGravityFrame(final TetrisInstance tetrisInstance) {
        tetrisInstance.setCurrentGravityFrame(0);
    }

    private static Map<Integer, Integer> createLevelSpeeds() {
        final int initialSpeed = 48;

        final Map<Integer, Integer> levelSpeeds = Maps.newHashMap();

        levelSpeeds.put(0, initialSpeed);
        levelSpeeds.put(9, 6);

        IntStream.range(1, 9).forEach(level -> levelSpeeds.put(level, initialSpeed - 5 * level));
        IntStream.range(10, 13).forEach(level -> levelSpeeds.put(level, 5));
        IntStream.range(13, 16).forEach(level -> levelSpeeds.put(level, 4));
        IntStream.range(16, 19).forEach(level -> levelSpeeds.put(level, 3));
        IntStream.range(19, 29).forEach(level -> levelSpeeds.put(level, 2));

        return levelSpeeds;
    }

    private int getLevelSpeed(final int level) {
        return level < 29 ? levelSpeeds.get(level) : 1;
    }

    private void moveShapeDown(final Tetris tetris) {
        moveShape(tetris, Movement.DOWN);
    }
}
