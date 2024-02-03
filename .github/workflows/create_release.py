import os
import requests
import json
import re
import subprocess

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

def get_new_tag_name(repo):
    tag_name = get_latest_release_tag(repo)
    print(f"get_new_tag_name() tag_name: {tag_name}")

    pattern = r'v(\d+?)\.(\d{1,2})\.(\d{1,2})(?:\.(\d+))?-beta$'
    
    match = re.search(pattern, tag_name)
    if match:
        groups = match.groups()
        last_group = groups[-1]
        
        if last_group is not None:
            incremented = int(last_group) + 1
        else:
            incremented = 0
        
        new_version = f"v{groups[0]}.{groups[1]}.{groups[2]}.{incremented}-beta"
        return new_version
    else:
        return ""
    
def get_commits_since(commit_hash, repo_path=None):
    if repo_path:
        os.chdir(repo_path)
    
    cmd = ["git", "log", f"{commit_hash}..HEAD", "--pretty=format:%s", "--date=local"]
    all_commits = ""
    
    try:
        output = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True)
        
        commits = output.strip().split('\n')
        for commit in commits:
            if commit.startswith('Merge'):
                continue

            all_commits += f"- {commit}\n"

        return all_commits
    except subprocess.CalledProcessError as e:
        print(f"Failed to get commits: {e.output}")
        return ""

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

if __name__ == "__main__":
    tag_name = get_new_tag_name('K1rakishou/Kuroba-Experimental-beta')
    if (len(tag_name) == 0):
        print("Failed to get the release tag.")
        exit(-1)

    latest_release_commit_hash = get_latest_release_commit_hash('K1rakishou/Kuroba-Experimental')
    if (len(latest_release_commit_hash) == 0):
         print("Failed to get latest release commit hash.")
         exit(-1)

    commits = get_commits_since(latest_release_commit_hash)

    print(f'tag_name: {tag_name}')
    print(f'commits:\n{commits}')

    repo = 'K1rakishou/Kuroba-Experimental-beta'
    release_name = f'KurobaEx-beta release {tag_name}'

    body = ""
    if (len(commits) > 0):
        body = f'New release available. It includes the following commits:\n{commits}'
    else:
        body = f'New release available.'

    asset_path = 'Kuroba/app/build/outputs/apk/beta/release/KurobaEx-beta.apk'

    token = os.getenv('PAT')
    if (len(token) == 0):
        print("Token is empty.")
        exit(-1)

    create_github_release(token, repo, tag_name, release_name, body, asset_path)