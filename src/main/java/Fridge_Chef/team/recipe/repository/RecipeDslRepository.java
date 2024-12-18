package Fridge_Chef.team.recipe.repository;

import Fridge_Chef.team.board.domain.Board;
import Fridge_Chef.team.board.domain.BoardUserEvent;
import Fridge_Chef.team.recipe.repository.model.RecipeSearchSortType;
import Fridge_Chef.team.recipe.rest.request.RecipePageRequest;
import Fridge_Chef.team.recipe.rest.response.RecipeSearchResponse;
import Fridge_Chef.team.user.domain.UserId;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static Fridge_Chef.team.board.domain.QBoard.board;
import static Fridge_Chef.team.board.domain.QBoardUserEvent.boardUserEvent;
import static Fridge_Chef.team.ingredient.domain.QIngredient.ingredient;
import static Fridge_Chef.team.recipe.domain.QRecipeIngredient.recipeIngredient;
import static Fridge_Chef.team.user.domain.QUser.user;

/**
 * 100개 레시피 단어 검색 [ 성능 최적화 기록 ]
 * 기존 단어만 검색시 Time: 1262ms (1 s 262 ms) ~ 1294ms (1 s 294 ms);
 * <p>
 * 이슈 : n+1 문제
 * n+1 문제 해결후 0.039ms 으로 해결
 * 이슈 : [게시판] - 1:N - [레시피-재료] - 1:1 - [재료]
 * 필수,선택 재료 매칭 정렬순 문제 해결
 * <p>
 * con = 400ms
 * cache = 218ms
 *
 * @author kang history
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RecipeDslRepository {
    private final JPAQueryFactory factory;


    @Transactional(readOnly = true)
    public Page<RecipeSearchResponse> findRecipesByIngredients(PageRequest pageable, RecipePageRequest request, List<String> must, List<String> ingredients, Optional<UserId> userId) {
        BooleanBuilder pickBuilder = new BooleanBuilder();
        BooleanBuilder mustBuilder = new BooleanBuilder();
        List<String> pick = merge(must, ingredients);

        var query = factory
                .selectFrom(board)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .leftJoin(board.context.boardIngredients, recipeIngredient)
                .leftJoin(recipeIngredient.ingredient, ingredient)
                .groupBy(board);

        validTask(must,
                () -> must.forEach(find -> mustBuilder.and(board.context.pathIngredient.contains(find))),
                () -> query.where(mustBuilder));

        validTask(ingredients,
                () -> pickBuilder.or(recipeIngredient.ingredient.name.in(pick)),
                () -> query.where(pickBuilder));

        int totalSize = query.fetch().size();

        applySort(query, request.sortType());

        List<Board> result = query.fetch();
        List<BoardUserEvent> userEvent = getUserEvent(result, userId);

        return PageableExecutionUtils.getPage(
                RecipeSearchResponse.of(result, pick, userEvent),
                pageable,
                () -> totalSize);
    }

    private List<BoardUserEvent> getUserEvent(List<Board> boards, Optional<UserId> userId) {
        if (userId.isPresent()) {
            List<Long> ids = boards.stream()
                    .map(Board::getId)
                    .toList();

            return factory.select(boardUserEvent)
                    .from(boardUserEvent)
                    .leftJoin(boardUserEvent.board, board)
                    .leftJoin(boardUserEvent.user, user)
                    .where(boardUserEvent.board.id.in(ids), boardUserEvent.user.userId.eq(userId.get()))
                    .where(boardUserEvent.hit.eq(1))
                    .fetch();
        }
        return new ArrayList<>();
    }

    private void validTask(List<String> ingredient, Runnable... runnables) {
        if (ingredient != null && !ingredient.isEmpty()) {
            Arrays.stream(runnables).forEach(Runnable::run);
        }
    }

    private void applySort(JPAQuery<Board> query, RecipeSearchSortType sortType) {
        switch (sortType) {
            case MATCH -> query.orderBy(recipeIngredient.ingredient.name.count().desc());
            case RATING -> query.orderBy(board.totalStar.desc());
            case LIKE -> query.orderBy(board.hit.desc());
            default -> query.orderBy(board.createTime.desc());
        }
    }

    private List<String> merge(List<String> left, List<String> right) {
        List<String> list = new ArrayList<>(left);
        list.addAll(right);
        return list;
    }
}
