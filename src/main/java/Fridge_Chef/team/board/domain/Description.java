package Fridge_Chef.team.board.domain;


import Fridge_Chef.team.image.domain.Image;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Description {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Board board;
    private String description;
    @ManyToOne(fetch = FetchType.LAZY)
    private Image image;

    public Description(String description, Image image) {
        this.description = description;
        this.image = image;
    }

    public Description update(String description, Image image) {
        this.description = description;
        this.image = image;
        return this;
    }

    public String getLink() {
        if (image != null) {
            return image.getLink();
        }
        return "";
    }
}