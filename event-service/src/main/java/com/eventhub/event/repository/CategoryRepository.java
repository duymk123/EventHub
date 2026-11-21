package com.eventhub.event.repository;

import com.eventhub.event.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** So sánh chạy trong MySQL nên theo cùng collation với UNIQUE(name). */
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<Category> findAllByOrderByNameAscIdAsc();
}
