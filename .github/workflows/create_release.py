import os
import requests
import json

def create_github_release(token, repo, tag_name, release_name, body, asset_path):
    url = f"https://api.github.com/repos/{repo}/releases"

    headers = {
        "Authorization": f"token {token}",
        "Content-Type": "application/json"
    }

    payload = {
        "tag_name": tag_name,
        "name": release_name,
        "body": body,
        "draft": False,
        "prerelease": False
    }

    response = requests.post(url, headers=headers, data=json.dumps(payload))

    if response.status_code != 201:
        print("Failed to create release.")
        print(response.status_code)
        print(response.content)
        exit(-1)

    print("Release created successfully.")
    upload_url = response.json()["upload_url"].split("{")[0]
    upload_asset(upload_url, asset_path, headers)

def upload_asset(upload_url, asset_path, headers):
    headers["Content-Type"] = "application/octet-stream"

    file_name = os.path.basename(asset_path)
    asset_url = f"{upload_url}?name={file_name}"

    with open(asset_path, "rb") as file:
        file_data = file.read()

    response = requests.post(asset_url, headers=headers, data=file_data)

    if response.status_code != 201:
        print("Failed to upload asset.")
        print(response.status_code)
        print(response.content)
        exit(-1)

    print("Asset uploaded successfully.")

if __name__ == "__main__":
    token = os.getenv('GITHUB_TOKEN')
    repo = 'K1rakishou/Kuroba-Experimental-beta'
    # TODO: use actual tag name which is going to be current version + build number
    tag_name = 'v1.0.0'
    # TODO: use actual release name based on tag + build number
    release_name = 'Test release'
    # TODO: use actual release description based on commits diff
    body = 'Test release body'
    asset_path = 'Kuroba/app/build/outputs/apk/beta/release/KurobaEx-beta.apk'

    print(token[:10])
    print(token[-10:])

    if (len(token) == 0):
        print("Token is empty.")
        exit(-1)

    create_github_release(token, repo, tag_name, release_name, body, asset_path)