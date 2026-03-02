.PHONY: local clean

include .env
export

local:
	docker compose up -d
	cd be && ./gradlew bootRun

clean:
	docker compose down
