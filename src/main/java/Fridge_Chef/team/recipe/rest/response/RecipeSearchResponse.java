package Fridge_Chef.team.recipe.rest.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeSearchResponse {

    private String name;
    private String imageUrl;
    private double totalStar;
    private int hit;
    private int totalIngredients;
    private int have;
    private List<String> without;
}