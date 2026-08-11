package ds.project.orino.planner.travel.place;

import ds.project.orino.planner.travel.external.ExternalApiRejectedException;
import ds.project.orino.planner.travel.place.client.PlaceResult;
import ds.project.orino.planner.travel.place.client.PlacesClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 테스트용 Places 스텁.
 *
 * <p>실제 구글을 부르면 <b>유료 호출이 발생하고</b> 결과도 시점마다 달라져 테스트가 흔들린다.
 * 대신 호출 횟수를 세어 <b>캐시가 실제로 호출을 줄이는지</b>를 검증할 수 있게 한다.
 */
public class StubPlacesClient implements PlacesClient {

    public final List<String> citySearches = new ArrayList<>();
    public final List<String> placeSearches = new ArrayList<>();
    public final List<Coordinates> biases = new ArrayList<>();
    public final List<String> detailFetches = new ArrayList<>();

    public List<PlaceResult> cityResults = List.of();
    public List<PlaceResult> placeResults = List.of();
    public Optional<PlaceResult> detailResult = Optional.empty();

    /** 켜면 구글이 429·403으로 거절한 것처럼 군다(#1159). 호출은 그대로 기록된다. */
    public boolean reject = false;

    @Override
    public List<PlaceResult> searchCities(String query) {
        citySearches.add(query);
        rejectIfAsked();
        return cityResults;
    }

    @Override
    public List<PlaceResult> searchPlaces(String query, Coordinates bias) {
        placeSearches.add(query);
        biases.add(bias);
        rejectIfAsked();
        return placeResults;
    }

    @Override
    public Optional<PlaceResult> fetchDetails(String googlePlaceId) {
        detailFetches.add(googlePlaceId);
        rejectIfAsked();
        return detailResult;
    }

    private void rejectIfAsked() {
        if (reject) {
            throw new ExternalApiRejectedException("stub 거절");
        }
    }

    public void reset() {
        citySearches.clear();
        placeSearches.clear();
        biases.clear();
        detailFetches.clear();
        reject = false;
    }
}
