package com.arvshop.customer.ui.catalog;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.customer.core.Result;
import com.arvshop.customer.core.ServiceLocator;
import com.arvshop.customer.data.model.Catalog;
import com.arvshop.customer.data.model.Product;
import com.arvshop.customer.data.repo.CatalogRepository;
import com.arvshop.customer.util.CatalogFilter;

import java.util.List;

/**
 * Catalog screen state: raw catalog result + search/category filters →
 * filtered product list. Survives rotation; repository survives the process.
 */
public class CatalogViewModel extends ViewModel {

    private final CatalogRepository repo = ServiceLocator.catalog();

    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<String> category =
            new MutableLiveData<>(CatalogFilter.ALL_CATEGORIES);
    private final MediatorLiveData<List<Product>> filtered = new MediatorLiveData<>();

    public CatalogViewModel() {
        filtered.addSource(repo.catalog(), r -> recompute());
        filtered.addSource(query, q -> recompute());
        filtered.addSource(category, c -> recompute());
        repo.load(false);
    }

    private void recompute() {
        Result<Catalog> r = repo.catalog().getValue();
        List<Product> products = (r != null && r.data != null) ? r.data.products : null;
        filtered.setValue(CatalogFilter.apply(products,
                query.getValue(), category.getValue()));
    }

    public LiveData<Result<Catalog>> catalog()      { return repo.catalog(); }
    public LiveData<Boolean> refreshing()           { return repo.refreshing(); }
    public LiveData<List<Product>> filteredProducts() { return filtered; }
    public LiveData<String> selectedCategory()      { return category; }

    public void setQuery(String q)      { query.setValue(q == null ? "" : q); }
    public void setCategory(String c)   { category.setValue(c == null ? CatalogFilter.ALL_CATEGORIES : c); }
    public void refresh()               { repo.load(true); }
    public void retry()                 { repo.load(false); }
}
