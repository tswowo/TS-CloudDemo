package com.hmall.search.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.search.domain.dto.ItemDoc;
import com.hmall.search.domain.po.Item;

public interface ISearchService extends IService<Item> {

    ItemDoc queryItemDocById(Long id);
}