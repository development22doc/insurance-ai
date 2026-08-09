import urllib.request
import urllib.error
import json

url = "http://localhost:8081/auth/authorize"

try:
    print("Making request to:", url)
    response = urllib.request.urlopen(url, timeout=5)
    print("HTTP Status Code:", response.status)
    print("Headers:", dict(response.headers))

    if response.status == 302:
        location = response.headers.get('Location')
        print("Location Header:", location)
except urllib.error.HTTPError as e:
    print("HTTP Status Code:", e.code)
    print("Headers:", dict(e.headers))
    print("Message:", e.msg)
    if e.code == 302:
        location = e.headers.get('Location')
        print("Location Header:", location)
except Exception as e:
    print("Error:", str(e))

