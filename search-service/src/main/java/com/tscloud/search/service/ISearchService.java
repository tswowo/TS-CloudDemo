package com.tscloud.search.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tscloud.common.domain.PageDTO;
import com.tscloud.search.domain.dto.ItemDTO;
import com.tscloud.search.domain.dto.ItemDoc;
import com.tscloud.search.domain.po.Item;
import com.tscloud.search.domain.query.ItemPageQuery;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ISearchService extends IService<Item> {

    ItemDoc queryItemDocById(Long id);

    PageDTO<ItemDTO> searchFromEsByCondition(ItemPageQuery query);

    Map<String, List<String>> getFilters(ItemPageQuery query);
}