package ds.project.orino.domain.category.repository;

import ds.project.orino.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByMemberIdOrderBySortOrder(Long memberId);

    Optional<Category> findByIdAndMemberId(Long id, Long memberId);
}
