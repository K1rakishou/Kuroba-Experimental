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

    if response.status_code == 201:
        print("Release created successfully.")
        upload_url = response.json()["upload_url"].split("{")[0]
        upload_asset(upload_url, asset_path, headers)
    else:
        print("Failed to create release.")
        print(response.content)

def upload_asset(upload_url, asset_path, headers):
    headers["Content-Type"] = "application/octet-stream"

    file_name = os.path.basename(asset_path)
    asset_url = f"{upload_url}?name={file_name}"

    with open(asset_path, "rb") as file:
        file_data = file.read()

    response = requests.post(asset_url, headers=headers, data=file_data)

    if response.status_code == 201:
        print("Asset uploaded successfully.")
    else:
        print("Failed to upload asset.")
        print(response.content)

if __name__ == "__main__":
    token = os.getenv('GITHUB_TOKEN')
    repo = 'K1rakishou/Kuroba-Experimental-beta'
    tag_name = 'v1.0.0'
    release_name = 'Test release'
    body = 'Test release body'
    asset_path = 'Kuroba/app/build/outputs/apk/beta/release/KurobaEx-beta.apk'

    create_github_release(token, repo, tag_name, release_name, body, asset_path)