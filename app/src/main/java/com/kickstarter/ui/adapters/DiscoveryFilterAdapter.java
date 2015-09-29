package com.kickstarter.ui.adapters;

import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.View;

import com.kickstarter.R;
import com.kickstarter.libs.AutoGson;
import com.kickstarter.models.Category;
import com.kickstarter.services.DiscoveryParams;
import com.kickstarter.ui.DiscoveryFilterStyle;
import com.kickstarter.ui.viewholders.DiscoveryFilterViewHolder;
import com.kickstarter.ui.viewholders.EmptyViewHolder;
import com.kickstarter.ui.viewholders.KsrViewHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import auto.parcel.AutoParcel;
import rx.Observable;

public class DiscoveryFilterAdapter extends KsrAdapter {
  private final Delegate delegate;
  private DiscoveryParams selectedDiscoveryParams;

  public interface Delegate extends DiscoveryFilterViewHolder.Delegate {}

  public DiscoveryFilterAdapter(@NonNull final Delegate delegate, @NonNull final DiscoveryParams selectedDiscoveryParams) {
    this.delegate = delegate;
    this.selectedDiscoveryParams = selectedDiscoveryParams;
  }

  protected int layout(@NonNull final SectionRow sectionRow) {
    if (sectionRow.section() == 1) {
      return R.layout.discovery_filter_divider_view;
    }
    return R.layout.discovery_filter_view;
  }

  protected KsrViewHolder viewHolder(final int layout, @NonNull final View view) {
    if (layout == R.layout.discovery_filter_divider_view) {
      return new EmptyViewHolder(view); // TODO: Might need to make a view holder here that toggles white or dark text
    }
    return new DiscoveryFilterViewHolder(view, delegate);
  }

  public void takeCategories(@NonNull final List<Category> initialCategories) {
    data().clear();

    data().addAll(paramsSections(initialCategories).toList().toBlocking().single());
    data().add(1, Collections.singletonList(null)); // Category divider

    notifyDataSetChanged();
  }

  /**
   * Returns an Observable where each item is a list of params/style pairs.
   */
  protected Observable<List<Filter>> paramsSections(@NonNull final List<Category> initialCategories) {
    return categoryFilters(initialCategories)
      .startWith(topFilters());
  }

  /**
   * Params for the top section of filters.
   */
  protected Observable<List<Filter>> topFilters() {
    final DiscoveryFilterStyle style = DiscoveryFilterStyle.builder().primary(true).selected(false).visible(true).build();

    // TODO: Add social filter
    return Observable.just(
      Filter.builder().params(DiscoveryParams.builder().staffPicks(true).build()).style(style).build(),
      Filter.builder().params(DiscoveryParams.builder().starred(1).build()).style(style).build(),
      Filter.builder().params(DiscoveryParams.builder().build()).style(style).build() // Everything filter
    ).toList();
  }

  /**
   * Transforms a list of categories into an Observable list of params.
   *
   * Each list of params has a duplicate root category. The duplicate will be used as a nested row under the
   * root downstream, e.g.:
   * Art
   *  - All of Art
   */
  protected Observable<List<Filter>> categoryFilters(@NonNull final List<Category> initialCategories) {
    final Observable<Category> categories = Observable.from(initialCategories);

    final Observable<Filter> filters = primaryCategoryFilters(categories.filter(Category::isRoot))
      .concatWith(secondaryCategoryFilters(categories))
      .toSortedList((f1, f2) -> f1.params().category().discoveryFilterCompareTo(f2.params().category()))
      .flatMap(Observable::from);

    // RxJava has groupBy. groupBy creates an Observable of GroupedObservables - the Observable doesn't complete
    // until all the GroupedObservables have been subscribed to and completed. It's quite confusing to work with,
    // refactor with caution.
    TreeMap<String, ArrayList<Filter>> groupedFilters = filters.reduce(new TreeMap<String, ArrayList<Filter>>(), (hash, filter) -> {
      final String key = filter.params().category().root().name();
      if (!hash.containsKey(key)) {
        hash.put(key, new ArrayList<Filter>());
      }
      hash.get(key).add(filter);
      return hash;
    }).toBlocking().single();

    return Observable.from(new ArrayList(groupedFilters.values()));
  }

  protected Observable<Filter> primaryCategoryFilters(@NonNull final Observable<Category> rootCategories) {
    return rootCategories.map(c -> Filter.builder()
      .params(DiscoveryParams.builder().category(c).build())
      .style((DiscoveryFilterStyle.builder().primary(true).selected(false).visible(true)).build())
      .build()); // TODO: Change selected
  }

  protected Observable<Filter> secondaryCategoryFilters(@NonNull final Observable<Category> categories) {
    return categories.map(c -> Filter.builder()
      .params(DiscoveryParams.builder().category(c).build())
      .style((DiscoveryFilterStyle.builder().primary(false).selected(false).visible(true)).build())
      .build()); // TODO: Change visible, selected
  }

  @AutoParcel
  public abstract static class Filter {
    public abstract DiscoveryParams params();
    public abstract DiscoveryFilterStyle style();

    @AutoParcel.Builder
    public abstract static class Builder {
      public abstract Builder params(DiscoveryParams __);
      public abstract Builder style(DiscoveryFilterStyle __);
      public abstract Filter build();
    }

    public static Builder builder() {
      return new AutoParcel_DiscoveryFilterAdapter_Filter.Builder();
    }
  }
}
