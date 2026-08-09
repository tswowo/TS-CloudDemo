package com.hmall.search.controller;

import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.dto.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.service.ISearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ISearchService searchService;

    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        return searchService.searchFromEsByCondition(query);
    }

    @ApiOperation("根据id查询商品")
    @GetMapping("/{id}")
    public ItemDoc searchById(@PathVariable Long id) {
        return searchService.queryItemDocById(id);
    }

    @ApiOperation("获取搜索过滤条件")
    @PostMapping("/filters")
    public Map<String, List<String>> getFilters(@RequestBody ItemPageQuery query) {
        return searchService.getFilters(query);
    }
}
