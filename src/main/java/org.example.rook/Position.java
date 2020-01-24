package org.example.rook;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@Getter
@EqualsAndHashCode(of = {"row", "column"})
@ToString
public class Position {
    private final int row;
    private final int column;
}
