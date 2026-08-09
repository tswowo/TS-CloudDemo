package com.hmall.search.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.dto.ItemDoc;
import com.hmall.search.domain.po.Item;
import com.hmall.search.domain.query.ItemPageQuery;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ISearchService extends IService<Item> {

    ItemDoc queryItemDocById(Long id);

    PageDTO<ItemDTO> searchFromEsByCondition(ItemPageQuery query);

    Map<String, List<String>> getFilters(ItemPageQuery query);
}