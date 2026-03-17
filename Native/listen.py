import asyncio
import websockets
import json

async def test_client():
    uri = "ws://localhost:8080/ws/metrics"

    async with websockets.connect(uri) as websocket:
        print("Connected")

        async for message in websocket:
            try:
                response_json = json.loads(message)
                print("Parsed response:", response_json)
                # print(f"Received response size: {len(message)}")
            except json.JSONDecodeError:
                print("Response was not valid JSON")

asyncio.run(test_client())


