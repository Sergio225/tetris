/*
 * Copyright © 2016 spypunk <spypunk@gmail.com>
 *
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See the COPYING file for more details.
 */

package spypunk.tetris.service;

import spypunk.tetris.model.Movement;
import spypunk.tetris.model.Tetris;
import spypunk.tetris.model.TetrisInstance;

public interface TetrisInstanceService {

    void create(Tetris tetris);

    void update(TetrisInstance tetrisInstance);

    void triggerMovement(TetrisInstance tetrisInstance, Movement movement);

    void triggerHardDrop(TetrisInstance tetrisInstance);

    void pause(TetrisInstance tetrisInstance);
}