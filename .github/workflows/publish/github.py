import os
import requests
import json
import helpers

def create_github_release(token, repo, tag_name, release_name, body, assets_path, apk_names: list[str], prerelease: bool):
    if len(apk_names) == 0:
        raise helpers.BuildCreationError("apk_names is empty")
    
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
        "prerelease": prerelease
    }

    response = requests.post(url, headers=headers, data=json.dumps(payload))
    if response.status_code != 201:
        raise helpers.BuildCreationError(f'Failed create release. StatusCode: {response.status_code}. Message: \'{response.content}\'')

    print("create_github_release() Release created successfully.")

    response_json = response.json()
    upload_url = response_json["upload_url"].split("{")[0]
    release_id = response_json["id"]
    print(f'create_github_release() tag_name: {tag_name}, release_id: {release_id}, upload_url: \'{upload_url}\'')
    
    for apk_name in apk_names:
        apk_path = f"{assets_path}/{apk_name}"
        print(f'create_github_release() uploading \'{apk_path}\'...')

        try:
            upload_asset(upload_url, apk_path, headers.copy())
            print(f'create_github_release() uploading \'{apk_path}\'... Success!')
        except Exception as e:
            print(f'create_github_release() uploading \'{apk_path}\'... ERROR ({e})!')
            print(f'create_github_release() deleting release {release_id}...')
            delete_github_release(token, repo, release_id)
            delete_github_tag(token, repo, tag_name)
            print(f'create_github_release() deleting release {release_id}... Success.')
            raise e

    print("create_github_release() Apks uploaded successfully!")

def delete_github_release(token, repo, release_id):
    url = f"https://api.github.com/repos/{repo}/releases/{release_id}"
    
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    response = requests.delete(url, headers=headers)
    
    if response.status_code == 204:
        print(f"Release {release_id} deleted successfully.")
    else:
        print(f"Failed to delete release. Status: {response.status_code}, Message: {response.content}")
    
def delete_github_tag(token, repo, tag_name):
    url = f"https://api.github.com/repos/{repo}/git/refs/tags/{tag_name}"
    
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    response = requests.delete(url, headers=headers)
    
    if response.status_code == 204:
        print(f"Tag {tag_name} deleted successfully.")
    else:
        print(f"Failed to delete tag. Status: {response.status_code}, Message: {response.content}")

def upload_asset(upload_url, apk_path, headers):
    headers["Content-Type"] = "application/vnd.android.package-archive"

    file_name = os.path.basename(apk_path)
    asset_url = f"{upload_url}?name={file_name}"
    
    with open(apk_path, "rb") as file:
        file_data = file.read()

    response = requests.post(asset_url, headers=headers, data=file_data)

    if response.status_code != 201:
        raise helpers.BuildCreationError(f'Failed to upload asset. StatusCode: {response.status_code}. Message: \'{response.content}\'')


def get_latest_release_tag(repo):
    url = f"https://api.github.com/repos/{repo}/releases/latest"
    response = requests.get(url)
    if response.status_code == 200:
        return response.json()['tag_name']
    else:
        return f"Error: {response.status_code}"

def get_latest_release_commit_hash(repo):
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
            return str(commit_hash)

    raise helpers.BuildCreationError("Failed to get latest release commit hash")
