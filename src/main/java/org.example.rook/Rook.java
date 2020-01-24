package org.example.rook;

import lombok.*;

@AllArgsConstructor
@Getter
@EqualsAndHashCode(of = "id")
@ToString
public class Rook {
    private final int id;

    @Setter
    private Position position;
}
