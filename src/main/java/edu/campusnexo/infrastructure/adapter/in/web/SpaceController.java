package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.infrastructure.adapter.in.web.dto.SpaceResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BASE + "/spaces")
class SpaceController {
    private final CampusCatalogUseCase catalog;

    SpaceController(CampusCatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<SpaceResponse> spaces(@RequestParam(required = false) SpaceType type) {
        return catalog.listSpaces(type).stream().map(SpaceResponse::from).toList();
    }

    @GetMapping("/{spaceId}")
    SpaceResponse space(@PathVariable String spaceId) {
        return SpaceResponse.from(catalog.getRequiredSpace(spaceId));
    }
}
