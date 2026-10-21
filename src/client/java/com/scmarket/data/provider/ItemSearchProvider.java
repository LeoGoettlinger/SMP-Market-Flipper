package com.scmarket.data.provider;

import com.scmarket.data.ItemSearchResult;

import java.util.List;

public interface ItemSearchProvider {
    List<ItemSearchResult> search(String query);
}

