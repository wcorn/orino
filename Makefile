.PHONY: local clean

include be/.env
export

local:
	docker compose up -d --build
	cd fe && npm run dev

clean:
	docker compose down
	@pkill -f "vite" 2>/dev/null || true
