"""Persistent history index with logarithmic append and historical lookup."""
from dataclasses import dataclass

@dataclass(frozen=True, slots=True)
class _Node:
    event: object
    left: object
    right: object
    height: int
    size: int

def _height(n):
    return n.height if n else 0

def _node(event, left=None, right=None):
    return _Node(event, left, right, 1 + max(_height(left), _height(right)), 1 + (left.size if left else 0) + (right.size if right else 0))

def _left(n):
    r = n.right
    return _node(r.event, _node(n.event, n.left, r.left), r.right)

def _right(n):
    l = n.left
    return _node(l.event, l.left, _node(n.event, l.right, n.right))

def append(tree, event):
    if tree is None:
        return _node(event)
    n = _node(tree.event, tree.left, append(tree.right, event))
    if _height(n.right) - _height(n.left) > 1:
        if _height(n.right.left) > _height(n.right.right):
            n = _node(n.event, n.left, _right(n.right))
        n = _left(n)
    return n

def entries(tree):
    stack = []
    while tree or stack:
        while tree:
            stack.append(tree)
            tree = tree.left
        tree = stack.pop()
        yield tree.event
        tree = tree.right

def before(tree, x):
    stack = []
    while tree:
        if tree.event[0]['at'] < x:
            stack.append(tree)
            tree = tree.right
        else:
            tree = tree.left
    while stack:
        n = stack.pop()
        yield n.event
        tree = n.left
        while tree:
            stack.append(tree)
            tree = tree.right
