"""Compatibility helpers for a single configured Google API key."""
import os

class APIKeyManager:
    def _key(self):
        value = os.environ.get("GOOGLE_API_KEY", "").strip()
        if not value:
            raise ValueError("GOOGLE_API_KEY is not configured.")
        return value

    def get_next_key(self):
        return self._key()

    def get_random_key(self):
        return self._key()

    def get_all_keys(self):
        return [self._key()]

    def get_key_count(self):
        return int(bool(os.environ.get("GOOGLE_API_KEY", "").strip()))

_manager = APIKeyManager()

def get_api_key_manager():
    return _manager

def get_next_api_key():
    return _manager.get_next_key()

def get_random_api_key():
    return _manager.get_random_key()
