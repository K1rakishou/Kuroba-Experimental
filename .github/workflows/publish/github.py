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

def get_latest_release_tag(owner_repo):
    url = f"https://api.github.com/repos/{owner_repo}/releases/latest"
    response = requests.get(url)
    if response.status_code == 200:
        return response.json()['tag_name']
    else:
        return f"Error: {response.status_code}"

def get_latest_release_commit_hash(repo, access_token=None):
    releases_url = f"https://api.github.com/repos/{repo}/releases/latest"
    release_response = requests.get(releases_url)

    if release_response.status_code == 200:
        release_data = release_response.json()
        tag_name = release_data['tag_name']

        tags_url = f"https://api.github.com/repos/{repo}/git/refs/tags/{tag_name}"
        tag_response = requests.get(tags_url)

        if tag_response.status_code == 200:
            tag_data = tag_response.json()
            commit_hash = tag_data['object']['sha']
            return commit_hash

    return ""
