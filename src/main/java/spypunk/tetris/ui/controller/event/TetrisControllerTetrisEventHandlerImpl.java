/*
 * Copyright © 2016-2017 spypunk <spypunk@gmail.com>
 *
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See the COPYING file for more details.
 */

package spypunk.tetris.ui.controller.event;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.collections4.CollectionUtils;

import com.google.common.collect.Maps;

import spypunk.tetris.model.TetrisEvent;
import spypunk.tetris.ui.controller.command.TetrisControllerCommand;
import spypunk.tetris.ui.factory.TetrisControllerCommandFactory;

@Singleton
public class TetrisControllerTetrisEventHandlerImpl implements TetrisControllerTetrisEventHandler {

    private final Map<TetrisEvent, Supplier<TetrisControllerCommand>> tetrisControllerCommands = Maps
            .newHashMap();

    @Inject
    public TetrisControllerTetrisEventHandlerImpl(final TetrisControllerCommandFactory tetrisControllerCommandFactory) {
        tetrisControllerCommands.put(TetrisEvent.SHAPE_LOCKED,
            tetrisControllerCommandFactory::createShapeLockedTetrisControllerCommand);

        tetrisControllerCommands.put(TetrisEvent.GAME_OVER,
            tetrisControllerCommandFactory::createGameOverTetrisControllerCommand);

        tetrisControllerCommands.put(TetrisEvent.ROWS_COMPLETED,
            tetrisControllerCommandFactory::createRowsCompletedTetrisControllerCommand);
    }

    @Override
    public List<TetrisControllerCommand> handleEvents(final List<TetrisEvent> tetrisEvents) {
        if (CollectionUtils.isEmpty(tetrisEvents)) {
            return Collections.emptyList();
        }

        return tetrisEvents.stream().map(tetrisControllerCommands::get).map(Supplier::get).collect(Collectors.toList());
    }

}
