// Persistent AVL index: appends share history; searches retain only overlapping handoffs.
const height = n => n ? n.height : 0;
function node(event, left = null, right = null) {
  return Object.freeze({event, left, right, height: 1 + Math.max(height(left), height(right)), size: 1 + (left?.size ?? 0) + (right?.size ?? 0)});
}
function left(n) { const r = n.right; return node(r.event, node(n.event, n.left, r.left), r.right); }
function right(n) { const l = n.left; return node(l.event, l.left, node(n.event, l.right, n.right)); }
export function append(tree, event) {
  if (!tree) return node(event);
  let n = node(tree.event, tree.left, append(tree.right, event));
  if (height(n.right) - height(n.left) > 1) {
    if (height(n.right.left) > height(n.right.right)) n = node(n.event, n.left, right(n.right));
    n = left(n);
  }
  return n;
}
export function* entries(tree) {
  const stack = [];
  while (tree || stack.length) {
    while (tree) { stack.push(tree); tree = tree.left; }
    tree = stack.pop(); yield tree.event; tree = tree.right;
  }
}
export function* before(tree, x) {
  const stack = [];
  while (tree) {
    if (tree.event.entry.at < x) { stack.push(tree); tree = tree.right; }
    else tree = tree.left;
  }
  while (stack.length) {
    const n = stack.pop(); yield n.event;
    tree = n.left;
    while (tree) { stack.push(tree); tree = tree.right; }
  }
}
